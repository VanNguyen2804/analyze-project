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
    private final Object cacheMonitor = new Object();
    private final java.util.concurrent.locks.ReentrantLock mutationLock = new java.util.concurrent.locks.ReentrantLock();
    private record ReadView(Map<String,Object> ledger, Map<String,Map<String,Object>> evaluations, Instant loadedAt, long expiresAt) {}
    private ReadView cachedView;
    private void invalidateView() { synchronized(cacheMonitor) { cachedView = null; } }
    @SuppressWarnings("unchecked")
    private ReadView readView() {
        synchronized(cacheMonitor) {
            if (cachedView != null && System.nanoTime() < cachedView.expiresAt()) return cachedView;
            var ledger = store.readLedger();
            var forecasts = (List<Map<String,Object>>)ledger.get("forecasts");
            var settlements = (List<Map<String,Object>>)ledger.get("settlements");
            Map<String,Map<String,Object>> evaluations = new HashMap<>();
            for (String category : List.of("ALL","MEGA","POWER"))
                evaluations.put(category, ForecastProtocol.evaluate(forecasts,settlements,"ALL".equals(category) ? null : category));
            cachedView = new ReadView(ledger,evaluations,Instant.now(),System.nanoTime()+10_000_000_000L);
            return cachedView;
        }
    }
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
            snapshot.put("explanation", PredictionExplanation.fromResult(prediction.getPrimaryAlgorithmResult()));
            if (!snapshot.get("targetDate").equals(prediction.getTargetDrawDate())) throw new IllegalStateException("TARGET_CHANGED_DURING_PREDICTION");
            var saved = store.capture(snapshot);
            invalidateView();
            prediction.setForecastSnapshot(ForecastProtocol.map("status","SAVED","id",saved.get("id"),"createdAt",saved.get("createdAt"),
                    "numbers",saved.get("numbers"),"sameAsDisplayed",saved.get("numbers").equals(snapshot.get("numbers"))));
        } catch (Exception e) {
            prediction.setForecastSnapshot(ForecastProtocol.map("status","NOT_SAVED","message",Objects.toString(e.getMessage(),e.getClass().getSimpleName())));
        }
        return prediction;
    }
    public Map<String,Object> captureAll() {
        if (!mutationLock.tryLock()) return ForecastProtocol.map("status","BUSY","message","Đang lưu hoặc đối soát; vui lòng chờ.");
        try {
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
        } finally { mutationLock.unlock(); }
    }
    public Map<String,Object> reconcile() {
        if (!mutationLock.tryLock()) return ForecastProtocol.map("status","BUSY","message","Đang lưu hoặc đối soát; vui lòng chờ.");
        try {
            var result = store.reconcile(draws.findAllByOrderByDrawDateDescCreatedAtDesc(),Instant.now());
            invalidateView();
            return result;
        } finally { mutationLock.unlock(); }
    }
    public Map<String,Object> list() {
        var result = new LinkedHashMap<>(readView().ledger()); result.put("enabled", enabled); result.put("lastRun",lastRun); return result;
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> evaluation(String category) {
        if (category != null && !Set.of("MEGA","POWER").contains(category)) throw new IllegalArgumentException("Invalid category");
        return readView().evaluations().get(category == null ? "ALL" : category);
    }
    /** Compact page payload; full immutable history remains available through the ledger endpoint. */
    @SuppressWarnings("unchecked")
    public Map<String,Object> overview(String category, int page, int size) {
        if (!Set.of("MEGA","POWER").contains(category) || page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid category or pagination");
        var view = readView();
        var forecasts = ((List<Map<String,Object>>)view.ledger().get("forecasts")).stream()
                .filter(f -> category.equals(f.get("category")))
                .sorted(Comparator.<Map<String,Object>,String>comparing(f -> (String)f.get("targetDate")).reversed().thenComparing(f -> (String)f.get("id"))).toList();
        Map<Object,Map<String,Object>> latest = new HashMap<>();
        ((List<Map<String,Object>>)view.ledger().get("settlements")).forEach(s -> latest.put(s.get("forecastId"),s));
        var rows = forecasts.stream().skip((long)page*size).limit(size).map(f -> {
            Map<String,Object> row = new LinkedHashMap<>();
            for (String key : List.of("id","category","targetDate","algorithm","modelVersion","createdAt","numbers")) row.put(key,f.get(key));
            row.put("settlement",latest.get(f.get("id"))); return row;
        }).toList();
        return ForecastProtocol.map("enabled",enabled,"lastRun",lastRun,"asOf",view.loadedAt().toString(),"cacheSeconds",10,
                "rows",rows,"page",page,"size",size,"totalRows",forecasts.size(),"evaluation",view.evaluations().get(category));
    }
    @Scheduled(initialDelay = 12000, fixedDelay = 60000)
    public void tick() {
        try { captureAll(); }
        catch(Exception e) { lastRun = ForecastProtocol.map("status","ERROR","message",Objects.toString(e.getMessage(),e.getClass().getSimpleName())); LoggerFactory.getLogger(ForecastService.class).warn("Forecast ledger retry on next tick", e); }
    }
}
