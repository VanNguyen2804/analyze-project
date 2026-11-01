package com.example.analyzeproject.repository;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.service.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Repository
public class MegaPortfolioStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json=new ObjectMapper();
    public MegaPortfolioStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Map<String,Object> decode(String value) { try { return json.readValue(value,new TypeReference<>() {}); } catch(Exception e) { throw new IllegalStateException(e); } }
    public Map<String,Object> find(LocalDate target) {
        var rows=jdbc.query("SELECT snapshot_json FROM mega_portfolio_snapshots WHERE target_date=?",(r,i)->decode(r.getString(1)),target);
        return rows.isEmpty()?null:rows.get(0);
    }
    @Transactional
    public Map<String,Object> capture(Map<String,Object> snapshot) {
        if (!MegaPortfolioProtocol.valid(snapshot)) throw new IllegalArgumentException("Invalid MEGA experiment snapshot");
        var target=LocalDate.parse((String)snapshot.get("targetDate"));
        jdbc.execute("SELECT pg_advisory_xact_lock(60452614)");
        var existing=find(target);
        if (existing!=null) return existing;
        jdbc.update("INSERT INTO mega_portfolio_snapshots(id,target_date,snapshot_json) VALUES (?,?,?) ON CONFLICT (target_date) DO NOTHING",
                snapshot.get("id"),target,encode(snapshot));
        return find(target);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> ledger() {
        return ForecastProtocol.map("forecasts",jdbc.query("SELECT snapshot_json FROM mega_portfolio_snapshots ORDER BY target_date DESC",(r,i)->decode(r.getString(1))),
                "settlements",jdbc.query("SELECT settlement_json FROM mega_portfolio_settlements ORDER BY id",(r,i)->decode(r.getString(1))));
    }
    @Transactional
    public void reconcile(List<LotteryNumber> draws,Instant now) {
        jdbc.execute("SELECT pg_advisory_xact_lock(60452614)");
        Map<Object,Map<String,Object>> latest=new HashMap<>();
        jdbc.query("SELECT settlement_json FROM mega_portfolio_settlements ORDER BY id",(r,i)->decode(r.getString(1))).forEach(e -> latest.put(e.get("forecastId"),e));
        var snapshots=jdbc.query("SELECT snapshot_json FROM mega_portfolio_snapshots ORDER BY target_date",(r,i)->decode(r.getString(1)));
        for (var snapshot:snapshots) {
            if (!MegaPortfolioProtocol.valid(snapshot)) continue;
            var previous=latest.get(snapshot.get("id"));
            var next=MegaPortfolioProtocol.settle(snapshot,draws,now,previous);
            if (previous==null || !Objects.equals(previous.get("status"),next.get("status")) || !Objects.equals(previous.get("resultFingerprint"),next.get("resultFingerprint")))
                jdbc.update("INSERT INTO mega_portfolio_settlements(forecast_id,settlement_json) VALUES (?,?)",snapshot.get("id"),encode(next));
        }
    }
}
