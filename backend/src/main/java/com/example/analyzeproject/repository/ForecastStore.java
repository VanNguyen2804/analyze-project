package com.example.analyzeproject.repository;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.service.ForecastProtocol;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Repository
public class ForecastStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    public ForecastStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Map<String,Object> decode(String value) { try { return json.readValue(value, new TypeReference<>() {}); } catch(Exception e) { throw new IllegalStateException(e); } }
    public List<Map<String,Object>> forecasts() { return jdbc.query("SELECT snapshot_json FROM forecast_snapshots ORDER BY id", (r,i) -> decode(r.getString(1))); }
    public List<Map<String,Object>> settlements() { return jdbc.query("SELECT settlement_json FROM forecast_settlements ORDER BY id", (r,i) -> decode(r.getString(1))); }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> readLedger() {
        return ForecastProtocol.map("forecasts", forecasts(), "settlements", settlements());
    }
    @Transactional
    public Map<String,Object> capture(Map<String,Object> forecast) {
        if (!ForecastProtocol.isProspective(forecast)) throw new IllegalArgumentException("Invalid prospective snapshot");
        jdbc.update("INSERT INTO forecast_snapshots(id,snapshot_json) VALUES (?,?) ON CONFLICT (id) DO NOTHING", forecast.get("id"), encode(forecast));
        return jdbc.queryForObject("SELECT snapshot_json FROM forecast_snapshots WHERE id=?", (r,i) -> decode(r.getString(1)), forecast.get("id"));
    }
    @Transactional
    public Map<String,Object> reconcile(List<LotteryNumber> rows, Instant now) {
        jdbc.execute("SELECT pg_advisory_xact_lock(60452611)");
        var forecasts = forecasts(); var events = settlements();
        Map<Object,Map<String,Object>> latest = new HashMap<>(); events.forEach(e -> latest.put(e.get("forecastId"),e));
        for (var forecast : forecasts) {
            var previous = latest.get(forecast.get("id"));
            var next = ForecastProtocol.settle(forecast, rows, now, previous);
            if (previous == null || !Objects.equals(previous.get("status"), next.get("status")) || !Objects.equals(previous.get("resultFingerprint"), next.get("resultFingerprint"))) {
                jdbc.update("INSERT INTO forecast_settlements(forecast_id,settlement_json) VALUES (?,?)", forecast.get("id"), encode(next));
                events.add(next);
            }
        }
        return ForecastProtocol.map("forecasts", forecasts, "settlements", events);
    }
}
