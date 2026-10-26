package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.PredictionResponseDto;
import com.example.analyzeproject.repository.ForecastStore;
import com.example.analyzeproject.repository.LotteryNumberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.*;

@Service
public class ForecastService {
    private final AnalyzeService analyze;
    private final LotteryNumberRepository draws;
    private final ForecastStore store;
    private final boolean enabled;
    private final String codeVersion = ForecastProtocol.codeVersion();
    private volatile Map<String,Object> lastRun = ForecastProtocol.map("status","NOT_RUN");
    public ForecastService(AnalyzeService analyze, LotteryNumberRepository draws, ForecastStore store,
            @Value("${forecast.enabled:true}") boolean enabled) {
        this.analyze = analyze; this.draws = draws; this.store = store; this.enabled = enabled;
    }
    private Map<String,Object> configuration(com.example.analyzeproject.model.AlgorithmHyperparameter active) {
        return active == null ? ForecastProtocol.map("version","default","hyperparametersJson","{}")
                : ForecastProtocol.map("version", active.getVersion(), "hyperparametersJson", active.getHyperparametersJson());
    }
    public PredictionResponseDto predict(String categoryInput, String algorithm, String algorithm2) {
        String category = "POWER".equalsIgnoreCase(categoryInput) ? "POWER" : "MEGA";
        var active = analyze.getLatestHyperparameter(category);
        var configuration = configuration(active);
        var history = ForecastProtocol.training(draws.findByCategoryOrderByDrawDateDescCreatedAtDesc(category),category,Instant.now());
        var prediction = analyze.analyzeAndPredictWithConfiguration(category, algorithm, algorithm2, history, active);
        if (!enabled) { prediction.setForecastSnapshot(ForecastProtocol.map("status","DISABLED")); return prediction; }
        try {
            if (prediction.getPrimaryAlgorithmResult() == null) throw new IllegalStateException("No primary ticket");
            @SuppressWarnings("unchecked")
            var numbers = (List<Integer>) prediction.getPrimaryAlgorithmResult().get("numbers");
            var snapshot = ForecastProtocol.prepare(history,category,prediction.getAlgorithm(),prediction.getModelVersion(),codeVersion,configuration,numbers,Instant.now());
            if (!snapshot.get("targetDate").equals(prediction.getTargetDrawDate())) throw new IllegalStateException("TARGET_CHANGED_DURING_PREDICTION");
            var saved = store.capture(snapshot);
            prediction.setForecastSnapshot(ForecastProtocol.map("status","SAVED","id",saved.get("id"),"createdAt",saved.get("createdAt"),
                    "numbers",saved.get("numbers"),"sameAsDisplayed",saved.get("numbers").equals(snapshot.get("numbers"))));
        } catch (Exception e) {
            prediction.setForecastSnapshot(ForecastProtocol.map("status","NOT_SAVED","message",Objects.toString(e.getMessage(),e.getClass().getSimpleName())));
        }
        return prediction;
    }
    public Map<String,Object> captureAll() {
        List<Map<String,Object>> captures = new ArrayList<>();
        Map<Object,Map<String,Object>> existing = new HashMap<>();
        store.forecasts().forEach(f -> existing.put(f.get("id"),f));
        if (enabled) for (String category : List.of("MEGA","POWER")) for (var algorithm : analyze.getAlgorithmCatalog()) {
            String id = (String) algorithm.get("id");
            String key = category + ":" + ForecastProtocol.target(category,Instant.now()) + ":" + id;
            if (existing.containsKey(key)) {
                captures.add(ForecastProtocol.map("category",category,"algorithm",id,"status","ALREADY_SAVED","id",key));
                continue;
            }
            try { captures.add(ForecastProtocol.map("category",category,"algorithm",id,"snapshot",predict(category,id,null).getForecastSnapshot())); }
            catch (Exception e) { captures.add(ForecastProtocol.map("category",category,"algorithm",id,"status","ERROR","message",Objects.toString(e.getMessage(),e.getClass().getSimpleName()))); }
        }
        Map<String,Object> reconciliation = reconcile();
        lastRun = ForecastProtocol.map("checkedAt",Instant.now().toString(),"captures",captures);
        return ForecastProtocol.map("lastRun",lastRun,"enabled",enabled,"forecasts",reconciliation.get("forecasts"));
    }
    public Map<String,Object> reconcile() {
        return store.reconcile(draws.findAllByOrderByDrawDateDescCreatedAtDesc(),Instant.now());
    }
    public Map<String,Object> list() {
        var result = reconcile(); result.put("enabled", enabled); result.put("lastRun",lastRun); return result;
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> evaluation(String category) {
        if (category != null && !Set.of("MEGA","POWER").contains(category)) throw new IllegalArgumentException("Invalid category");
        var ledger = reconcile();
        return ForecastProtocol.evaluate((List<Map<String,Object>>) ledger.get("forecasts"),(List<Map<String,Object>>) ledger.get("settlements"),category);
    }
    @Scheduled(initialDelay = 12000, fixedDelay = 60000)
    public void tick() {
        try { captureAll(); }
        catch(Exception e) { lastRun = ForecastProtocol.map("status","ERROR","message",Objects.toString(e.getMessage(),e.getClass().getSimpleName())); LoggerFactory.getLogger(ForecastService.class).warn("Forecast ledger retry on next tick", e); }
    }
}
