package com.example.analyzeproject.repository;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.service.ForecastProtocol;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;

@Repository
public class AiLabStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    public AiLabStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Map<String,Object> decode(String value) { try { return json.readValue(value,new TypeReference<>() {}); } catch(Exception e) { throw new IllegalStateException(e); } }
    public List<Map<String,Object>> snapshots() { return jdbc.query("SELECT snapshot_json FROM ai_lab_snapshots ORDER BY id",(r,i)->decode(r.getString(1))); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> ledger() {
        return ForecastProtocol.map("forecasts",snapshots(),"settlements",jdbc.query("SELECT settlement_json FROM ai_lab_settlements ORDER BY id",(r,i)->decode(r.getString(1))));
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
        jdbc.query("SELECT settlement_json FROM ai_lab_settlements ORDER BY id",(r,i)->decode(r.getString(1))).forEach(e->latest.put(e.get("forecastId"),e));
        for(var f:snapshots()) {
            var previous=latest.get(f.get("id")); var next=ForecastProtocol.settle(f,rows,now,previous);
            if(previous==null || !Objects.equals(previous.get("status"),next.get("status")) || !Objects.equals(previous.get("resultFingerprint"),next.get("resultFingerprint")))
                jdbc.update("INSERT INTO ai_lab_settlements(forecast_id,settlement_json) VALUES (?,?)",f.get("id"),encode(next));
        }
    }
}
