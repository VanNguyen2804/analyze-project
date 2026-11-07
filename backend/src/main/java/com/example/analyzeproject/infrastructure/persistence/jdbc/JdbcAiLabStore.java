package com.example.analyzeproject.infrastructure.persistence.jdbc;
import com.example.analyzeproject.application.port.out.AiLabStore;

import com.example.analyzeproject.domain.service.*;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.application.service.ForecastProtocol;
import com.example.analyzeproject.application.dto.AiLabReadView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcAiLabStore implements AiLabStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    public JdbcAiLabStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Map<String,Object> decode(String value) { try { return json.readValue(value,new TypeReference<>() {}); } catch(Exception e) { throw new IllegalStateException(e); } }
    public List<Map<String,Object>> snapshots() { return jdbc.query("SELECT snapshot_json FROM ai_lab_snapshots ORDER BY id",(r,i)->decode(r.getString(1))); }
    @Override public Set<String> snapshotIds() { return new HashSet<>(jdbc.query("SELECT id FROM ai_lab_snapshots",(r,i)->r.getString(1))); }
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Map<String,Object>> feedbackSnapshots(String category,java.time.LocalDate target,int maximumDraws,Instant now) {
        if(!Set.of("MEGA","POWER").contains(category)||maximumDraws<1||maximumDraws>120)throw new IllegalArgumentException("Invalid feedback window");
        return jdbc.query(connection->{var statement=connection.prepareStatement("SELECT snapshot_json FROM ai_lab_snapshots WHERE id LIKE ? AND id < ? ORDER BY id DESC");
                    statement.setString(1,category+":%:lab-logistic-%");statement.setString(2,category+":"+target+":");statement.setFetchSize(5);return statement;},
                (org.springframework.jdbc.core.ResultSetExtractor<List<Map<String,Object>>>)cursor->{
                    Set<Object> dates=new LinkedHashSet<>();List<Map<String,Object>> result=new ArrayList<>();
                    while(cursor.next()) {
                        var f=decode(cursor.getString(1));if(!com.example.analyzeproject.application.service.AiLabFeedback.eligible(f,category,target,now))continue;
                        if(!dates.contains(f.get("targetDate"))&&dates.size()>=maximumDraws)break;
                        dates.add(f.get("targetDate"));result.add(f);
                    }return result;
                });
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> ledger() {
        return ForecastProtocol.map("forecasts",snapshots(),"settlements",jdbc.query("SELECT settlement_json FROM ai_lab_settlements ORDER BY id",(r,i)->decode(r.getString(1))));
    }
    /** Stream one full snapshot at a time; retain only the small, validated page/evaluation projection. */
    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AiLabReadView readOverview(String category) {
        if(!Set.of("MEGA","POWER").contains(category))throw new IllegalArgumentException("Invalid category");
        List<AiLabReadView.Snapshot> projected=new ArrayList<>();
        jdbc.query(connection->{var statement=connection.prepareStatement("SELECT snapshot_json FROM ai_lab_snapshots WHERE id LIKE ? ORDER BY id");
                    statement.setString(1,category+":%");statement.setFetchSize(25);return statement;},
                (org.springframework.jdbc.core.RowCallbackHandler)row->{var raw=decode(row.getString(1));
                    if(category.equals(raw.get("category")))projected.add(AiLabReadView.project(raw));});
        var events=jdbc.query("SELECT DISTINCT ON (forecast_id) settlement_json FROM ai_lab_settlements WHERE forecast_id LIKE ? ORDER BY forecast_id,id DESC",
                (r,i)->decode(r.getString(1)),category+":%");
        return new AiLabReadView(projected,events);
    }
    @Transactional
    public void captureBatch(List<Map<String,Object>> snapshots) {
        for(var f:snapshots) {
            if(!ForecastProtocol.isProspective(f)) throw new IllegalArgumentException("Invalid lab snapshot");
            jdbc.update("INSERT INTO ai_lab_snapshots(id,snapshot_json) VALUES (?,?) ON CONFLICT (id) DO NOTHING",f.get("id"),encode(f));
        }
    }
    @Transactional
    public void reconcile(List<LotteryNumber> rows,Instant now) {
        jdbc.execute("SELECT pg_advisory_xact_lock(60452612)");
        Map<Object,Map<String,Object>> latest=new HashMap<>();
        jdbc.query("SELECT DISTINCT ON (forecast_id) settlement_json FROM ai_lab_settlements ORDER BY forecast_id,id DESC",(r,i)->decode(r.getString(1))).forEach(e->latest.put(e.get("forecastId"),e));
        Map<String,LotteryNumber> actual=new HashMap<>();DrawQuality.history(rows).forEach(r->actual.put(r.getCategory()+":"+r.getDrawDate(),r));
        List<String> changed=new ArrayList<>();
        for(String id:snapshotIds())if(!unchanged(id,latest.get(id),actual,now))changed.add(id);
        for(int offset=0;offset<changed.size();offset+=50) {
            var batch=changed.subList(offset,Math.min(offset+50,changed.size()));
            String placeholders=String.join(",",Collections.nCopies(batch.size(),"?"));
            jdbc.query(connection->{var statement=connection.prepareStatement("SELECT snapshot_json FROM ai_lab_snapshots WHERE id IN ("+placeholders+")");
                        for(int i=0;i<batch.size();i++)statement.setString(i+1,batch.get(i));statement.setFetchSize(5);return statement;},
                    (org.springframework.jdbc.core.RowCallbackHandler)row->{var f=decode(row.getString(1));var previous=latest.get(f.get("id"));
                        var next=ForecastProtocol.settleSelected(f,actual.get(f.get("category")+":"+f.get("targetDate")),now,previous);
                        if(previous==null || !Objects.equals(previous.get("status"),next.get("status")) || !Objects.equals(previous.get("resultFingerprint"),next.get("resultFingerprint")))
                            jdbc.update("INSERT INTO ai_lab_settlements(forecast_id,settlement_json) VALUES (?,?)",f.get("id"),encode(next));});
        }
    }
    private static boolean unchanged(String id,Map<String,Object> previous,Map<String,LotteryNumber> actuals,Instant now) {
        if(previous==null)return false;
        try {
            String[] key=id.split(":",3);var date=java.time.LocalDate.parse(key[1]);var actual=actuals.get(key[0]+":"+key[1]);
            boolean ready=actual!=null&&!now.isBefore(date.atTime(19,0).atZone(ForecastProtocol.ZONE).toInstant());
            String status=ready?"SETTLED":Set.of("SETTLED","RESULT_UNAVAILABLE").contains(previous.get("status"))?"RESULT_UNAVAILABLE":"PENDING";
            return status.equals(previous.get("status"))&&Objects.equals(ready?DrawQuality.fingerprint(actual):null,previous.get("resultFingerprint"));
        }catch(RuntimeException e){return false;}
    }
}
