package com.example.analyzeproject.infrastructure.persistence.jdbc;

import com.example.analyzeproject.application.port.out.PageJobStore;
import com.example.analyzeproject.application.exception.ServiceBusyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.util.*;

public class JdbcPageJobStore implements PageJobStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final int capacity;
    public JdbcPageJobStore(JdbcTemplate jdbc,ObjectMapper json,int capacity) {
        this.jdbc=jdbc;this.json=json;this.capacity=capacity;
    }
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Object decode(String value) {try{return value==null?null:json.readValue(value,Object.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private Job view(ResultSet r,int ignored) throws SQLException {
        return new Job(r.getString("id"),r.getString("status"),decode(r.getString("result_json")),
                "FAILED".equals(r.getString("status"))?"Không hoàn tất tác vụ. Hãy thử lại.":null,r.getTimestamp("updated_at").toInstant());
    }
    @Override public String revision(){return jdbc.queryForObject("SELECT revision::text FROM page_job_revision WHERE id=1",String.class);}
    @Override @Transactional
    public Job enqueue(String key,String kind,Map<String,Object> input,int cacheSeconds,Instant now) {
        jdbc.execute("SELECT pg_advisory_xact_lock(60452619)");
        jdbc.update("UPDATE page_jobs SET status='FAILED',updated_at=? WHERE status='QUEUED' AND created_at<?",Timestamp.from(now),Timestamp.from(now.minusSeconds(600)));
        jdbc.update("DELETE FROM page_jobs WHERE status IN ('SUCCEEDED','FAILED') AND updated_at < ?",Timestamp.from(now.minusSeconds(3600)));
        var existing=jdbc.query("SELECT * FROM page_jobs WHERE job_key=? AND (status IN ('QUEUED','RUNNING') OR (status='SUCCEEDED' AND expires_at>?)) ORDER BY created_at DESC LIMIT 1",this::view,key,Timestamp.from(now));
        if(!existing.isEmpty())return existing.get(0);
        Integer pending=jdbc.queryForObject("SELECT count(*) FROM page_jobs WHERE status IN ('QUEUED','RUNNING')",Integer.class);
        if(pending>=capacity)throw new ServiceBusyException("Hệ thống đang xử lý nhiều yêu cầu. Hãy thử lại sau ít giây.");
        String id="l-"+UUID.randomUUID();
        jdbc.update("INSERT INTO page_jobs(id,job_key,kind,input_json,status,cache_seconds,created_at,updated_at) VALUES (?,?,?,?,'QUEUED',?,?,?)",id,key,kind,encode(input),cacheSeconds,Timestamp.from(now),Timestamp.from(now));
        return new Job(id,"QUEUED",null,null,now);
    }
    @Override public Job find(String id) {var result=jdbc.query("SELECT * FROM page_jobs WHERE id=?",this::view,id);return result.isEmpty()?null:result.get(0);}
    @Override @Transactional
    @SuppressWarnings("unchecked")
    public Work claim(Instant now) {
        jdbc.update("UPDATE page_jobs SET status='FAILED',updated_at=? WHERE status='QUEUED' AND created_at<?",Timestamp.from(now),Timestamp.from(now.minusSeconds(600)));
        // Recover claims abandoned by a restarted process; a previous owner cannot publish after recovery.
        jdbc.update("UPDATE page_jobs SET status=CASE WHEN attempts<2 AND kind NOT IN ('ANALYZE','LAB_RUN') THEN 'QUEUED' ELSE 'FAILED' END, updated_at=?,claim_token=NULL WHERE status='RUNNING' AND lease_until<?",Timestamp.from(now),Timestamp.from(now));
        String token=UUID.randomUUID().toString();
        var work=jdbc.query("UPDATE page_jobs SET status='RUNNING',attempts=attempts+1,claim_token=?,lease_until=?,updated_at=? WHERE id=(SELECT id FROM page_jobs WHERE status='QUEUED' ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING id,kind,input_json",
                (r,i)->new Work(r.getString(1),r.getString(2),(Map<String,Object>)decode(r.getString(3)),token),token,Timestamp.from(now.plusSeconds(60)),Timestamp.from(now));
        return work.isEmpty()?null:work.get(0);
    }
    @Override public void complete(Work work,Object result,Instant now) {
        jdbc.update("UPDATE page_jobs SET status='SUCCEEDED',result_json=?,updated_at=?,expires_at=CAST(? AS timestamptz)+(cache_seconds * interval '1 second'),claim_token=NULL WHERE id=? AND claim_token=? AND status='RUNNING'",
                encode(result),Timestamp.from(now),Timestamp.from(now),work.id(),work.token());
    }
    @Override public void fail(Work work,Instant now) {
        jdbc.update("UPDATE page_jobs SET status='FAILED',updated_at=?,claim_token=NULL WHERE id=? AND claim_token=? AND status='RUNNING'",Timestamp.from(now),work.id(),work.token());
    }
    @Override public void heartbeat(Work work,Instant now) {
        jdbc.update("UPDATE page_jobs SET lease_until=? WHERE id=? AND claim_token=? AND status='RUNNING'",Timestamp.from(now.plusSeconds(60)),work.id(),work.token());
    }
}
