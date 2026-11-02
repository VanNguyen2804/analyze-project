package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.service.*;

import com.example.analyzeproject.application.port.out.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import static com.example.analyzeproject.application.service.ForecastProtocol.map;

public class MegaPortfolioService {
    private final AnalyzeService analyze;
    private final LotteryNumberRepository draws;
    private final MegaPortfolioStore store;
    private final ForecastStore forecasts;
    private final AiLabStore lab;
    private final boolean enabled;
    private final Clock clock;
    private final ReentrantLock lock=new ReentrantLock();
    private final String codeVersion=WorkspaceProtocol.fingerprint(MegaPortfolioProtocol.class,TicketPortfolio.class,
            AnalyzeService.class,TemporalRelationshipModel.class,PredictionSupport.class,ForecastProtocol.class,DrawQuality.class,WorkspaceProtocol.class);
    private volatile Map<String,Object> lastRun=map("status","NOT_RUN");
    public MegaPortfolioService(AnalyzeService analyze,LotteryNumberRepository draws,MegaPortfolioStore store,ForecastStore forecasts,AiLabStore lab,
            boolean enabled) {
        this(analyze,draws,store,enabled,Clock.systemUTC(),forecasts,lab);
    }
    public MegaPortfolioService(AnalyzeService analyze,LotteryNumberRepository draws,MegaPortfolioStore store,boolean enabled,Clock clock) {
        this(analyze,draws,store,enabled,clock,null,null);
    }
    public MegaPortfolioService(AnalyzeService analyze,LotteryNumberRepository draws,MegaPortfolioStore store,boolean enabled,Clock clock,ForecastStore forecasts,AiLabStore lab) {
        this.analyze=analyze; this.draws=draws; this.store=store; this.enabled=enabled; this.clock=clock;
        this.forecasts=forecasts;this.lab=lab;
    }
    @SuppressWarnings("unchecked")
    private List<List<Integer>> existingTickets(String target) {
        var saved=new ArrayList<Map<String,Object>>();
        if (forecasts!=null) saved.addAll(forecasts.forecasts());
        if (lab!=null) saved.addAll(lab.snapshots());
        return saved.stream().filter(f -> "MEGA".equals(f.get("category")) && target.equals(f.get("targetDate")) && ForecastProtocol.isProspective(f))
                .map(f -> WorkspaceProtocol.numbers("MEGA",(List<Integer>)f.get("numbers"))).distinct().toList();
    }
    public Map<String,Object> run() {
        if (!lock.tryLock()) return map("status","BUSY");
        try {
            Instant now=clock.instant(); var all=draws.findAllByOrderByDrawDateDescCreatedAtDesc();
            String status=enabled?"ALREADY_SAVED":"DISABLED"; String message=null;
            if (enabled) {
                var target=ForecastProtocol.target("MEGA",now);
                if (store.find(target)==null) try {
                    var history=ForecastProtocol.training(all,"MEGA",now);
                    if (history.size()<10) throw new IllegalStateException("Cần ít nhất 10 kỳ lịch sử MEGA hợp lệ.");
                    // Validate freshness before scoring. Freeze defaults to match the development experiment.
                    ForecastProtocol.prepare(history,"MEGA",MegaPortfolioProtocol.VERSION,MegaPortfolioProtocol.VERSION,codeVersion,Map.of(),List.of(1,2,3,4,5,6),now);
                    var prediction=analyze.analyzeAndPredictWithConfiguration("MEGA","temporal_learning",null,history,null);
                    Instant captured=clock.instant();
                    if (!target.equals(ForecastProtocol.target("MEGA",captured))) throw new IllegalStateException("Kỳ mục tiêu đã thay đổi; sẽ thử lại.");
                    var snapshot=MegaPortfolioProtocol.prepare(history,prediction,codeVersion,captured,existingTickets(target.toString()));
                    Instant completed=clock.instant();
                    if (!target.equals(ForecastProtocol.target("MEGA",completed))) throw new IllegalStateException("Đã hết giờ chốt; sẽ chờ kỳ mới đủ dữ liệu.");
                    snapshot.put("createdAt",completed.toString());
                    store.capture(snapshot);
                    status="SAVED";
                } catch (Exception e) { status="NOT_SAVED"; message=Objects.toString(e.getMessage(),"Chưa lưu được bộ thử nghiệm."); }
            }
            store.reconcile(all,clock.instant());
            lastRun=map("status",status,"message",message,"checkedAt",clock.instant().toString());
            return lastRun;
        } finally { lock.unlock(); }
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> overview(int page) {
        if (page<0) throw new IllegalArgumentException("Trang không hợp lệ.");
        var ledger=store.ledger(); var all=(List<Map<String,Object>>)ledger.get("forecasts");
        var snapshots=all.stream().filter(MegaPortfolioProtocol::valid)
                .sorted(Comparator.<Map<String,Object>,String>comparing(f -> (String)f.get("targetDate")).reversed()).toList();
        Map<Object,Map<String,Object>> latest=new HashMap<>();
        ((List<Map<String,Object>>)ledger.get("settlements")).forEach(e -> latest.put(e.get("forecastId"),e));
        String target=ForecastProtocol.target("MEGA",clock.instant()).toString();
        var current=snapshots.stream().filter(f -> target.equals(f.get("targetDate")) && MegaPortfolioProtocol.VERSION.equals(f.get("modelVersion"))).findFirst().orElse(null);
        return map("version",MegaPortfolioProtocol.VERSION,"category","MEGA","budget",MegaPortfolioProtocol.BUDGET,"enabled",enabled,
                "targetDate",target,"lastRun",lastRun,"current",current==null?null:MegaPortfolioProtocol.row(current,latest.get(current.get("id"))),
                "page",page,"totalRows",snapshots.size(),"excludedInvalidSnapshots",all.size()-snapshots.size(),
                "rows",snapshots.stream().skip((long)page*10).limit(10).map(f -> MegaPortfolioProtocol.row(f,latest.get(f.get("id")))).toList(),
                "groups",MegaPortfolioProtocol.evaluate(snapshots,latest));
    }
    public void tick() {
        try { run(); } catch (Exception e) { lastRun=map("status","ERROR","checkedAt",clock.instant().toString(),"message",Objects.toString(e.getMessage(),"Error")); }
    }
}
