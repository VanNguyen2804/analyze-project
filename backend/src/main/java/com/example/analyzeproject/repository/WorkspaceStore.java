package com.example.analyzeproject.repository;

import com.example.analyzeproject.dto.WorkspaceRequests.Commit;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.service.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static com.example.analyzeproject.service.ForecastProtocol.map;

@Repository
public class WorkspaceStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    public WorkspaceStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Map<String,Object> decode(String value) { try { return json.readValue(value,new TypeReference<>(){}); } catch(Exception e) { throw new IllegalStateException(e); } }
    public void saveSuggestion(Map<String,Object> value) {
        jdbc.update("INSERT INTO prediction_suggestions(id,category,target_date,suggestion_json) VALUES (?,?,CAST(? AS DATE),?)",value.get("id"),value.get("category"),value.get("targetDate"),encode(value));
    }
    public Map<String,Object> suggestion(String id) {
        return jdbc.query("SELECT suggestion_json FROM prediction_suggestions WHERE id=?",(r,i)->decode(r.getString(1)),id).stream().findFirst()
                .orElseThrow(()->new IllegalArgumentException("Không tìm thấy bản gợi ý."));
    }
    public int revision(String category,String date) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(revision),0) FROM prediction_user_batches WHERE category=? AND target_date=CAST(? AS DATE)",Integer.class,category,date);
    }
    @Transactional
    public Map<String,Object> commit(Commit request, Supplier<Map<String,Object>> build) {
        jdbc.execute("SELECT pg_advisory_xact_lock(60452613)");
        String hash=ForecastProtocol.hash(request);
        var prior=jdbc.query("SELECT payload_hash,batch_json FROM prediction_user_batches WHERE request_key=?",(r,i)->map("hash",r.getString(1),"batch",decode(r.getString(2))),request.requestKey());
        if(!prior.isEmpty()) {
            if(!hash.equals(prior.get(0).get("hash"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã gửi lại thuộc một danh sách vé khác.");
            @SuppressWarnings("unchecked") var saved=(Map<String,Object>)prior.get(0).get("batch"); return saved;
        }
        int current=revision(request.category(),request.targetDate());
        if(current!=request.expectedRevision()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Có bản chốt mới từ lượt khác. Hãy tải lại lịch sử trước khi chốt.");
        var value=build.get(); value.put("revision",current+1);
        // Recheck after all validation, immediately before insertion within the transaction.
        Instant savedAt=Instant.now();
        if(!savedAt.isBefore(LocalDate.parse(request.targetDate()).atTime(18,0).atZone(ForecastProtocol.ZONE).toInstant()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Đã hết giờ chốt kỳ này.");
        value.put("committedAt",savedAt.toString());
        jdbc.update("INSERT INTO prediction_user_batches(id,category,target_date,revision,request_key,payload_hash,batch_json) VALUES (?,?,CAST(? AS DATE),?,?,?,?)",
                value.get("id"),request.category(),request.targetDate(),current+1,request.requestKey(),hash,encode(value));
        return value;
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> history(String category) {
        var batches=jdbc.query("SELECT batch_json FROM prediction_user_batches WHERE category=? ORDER BY target_date DESC,revision DESC",(r,i)->decode(r.getString(1)),category);
        var settlements=jdbc.query("SELECT s.settlement_json FROM prediction_user_settlements s JOIN prediction_user_batches b ON b.id=s.batch_id WHERE b.category=? ORDER BY s.id",(r,i)->decode(r.getString(1)),category);
        Map<Object,Map<String,Object>> latest=new HashMap<>(); settlements.forEach(s->latest.put(s.get("batchId"),s));
        Set<Object> dates=new HashSet<>();
        for(var b:batches) { b.put("active",dates.add(b.get("targetDate"))); b.put("settlement",latest.get(b.get("id"))); }
        return map("batches",batches);
    }
    @Transactional
    public void reconcile(List<LotteryNumber> rows,Instant now) {
        jdbc.execute("SELECT pg_advisory_xact_lock(60452614)");
        Map<Object,Map<String,Object>> previous=new HashMap<>();
        jdbc.query("SELECT settlement_json FROM prediction_user_settlements ORDER BY id",(r,i)->decode(r.getString(1))).forEach(s->previous.put(s.get("batchId"),s));
        var batches=jdbc.query("SELECT batch_json FROM prediction_user_batches ORDER BY target_date,revision",(r,i)->decode(r.getString(1)));
        for(var batch:batches) {
            var old=previous.get(batch.get("id")); var next=WorkspaceService.settle(batch,rows,now,old);
            if(old==null || !Objects.equals(old.get("status"),next.get("status")) || !Objects.equals(old.get("resultFingerprint"),next.get("resultFingerprint")))
                jdbc.update("INSERT INTO prediction_user_settlements(batch_id,settlement_json) VALUES (?,?)",batch.get("id"),encode(next));
        }
    }
}
