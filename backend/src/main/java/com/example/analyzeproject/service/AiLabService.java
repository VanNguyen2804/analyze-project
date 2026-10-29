package com.example.analyzeproject.service;

import com.example.analyzeproject.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import static com.example.analyzeproject.service.ForecastProtocol.map;

@Service
public class AiLabService {
    private final AiLabStore store;
    private final LotteryNumberRepository draws;
    private final boolean enabled;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Map<String,Object> lastRun=map("status","NOT_RUN");
    private final String codeVersion;
    public AiLabService(AiLabStore store,LotteryNumberRepository draws,@Value("${ai-lab.enabled:true}") boolean enabled) {
        this.store=store; this.draws=draws; this.enabled=enabled;
        codeVersion=WorkspaceProtocol.fingerprint(AiLabModel.class,AiLabService.class,ForecastProtocol.class,DrawQuality.class);
    }
    public Map<String,Object> run() {
        if(!lock.tryLock()) return map("status","BUSY");
        try {
            Instant now=Instant.now(); var all=draws.findAllByOrderByDrawDateDescCreatedAtDesc();
            var existing=store.snapshots(); Set<Object> ids=new HashSet<>(); existing.forEach(f->ids.add(f.get("id")));
            List<Map<String,Object>> outcomes=new ArrayList<>();
            if(enabled) for(String category:List.of("MEGA","POWER")) {
                try {
                    var history=ForecastProtocol.training(all,category,now);
                    if(history.size()<10) throw new IllegalStateException("HISTORY_NOT_READY: cần ít nhất 10 kỳ hợp lệ để huấn luyện");
                    String target=ForecastProtocol.target(category,now).toString();
                    for(String model:AiLabModel.MODELS) {
                        String prefix=category+":"+target+":lab-"+model+"-";
                        if(ids.contains(prefix+0)) { outcomes.add(map("category",category,"model",model,"status","ALREADY_SAVED")); continue; }
                        var config=map("protocol",AiLabModel.VERSION,"budget",AiLabModel.BUDGET,"epochs",80,"learningRate",0.2,"l2",0.05,
                                "features",List.of("frequency","recent10","decay10","gap30"),"minimumDraws",60,"windowDraws",30,"minimumWindowHitRate",0.5);
                        // Validate freshness before fitting; do not capture a partially trained or stale batch.
                        ForecastProtocol.prepare(history,category,"validation",AiLabModel.VERSION,codeVersion,config,List.of(1,2,3,4,5,6),now);
                        String seed=AiLabModel.VERSION+":"+category+":"+target;
                        var fit=AiLabModel.fit(history,category,model);var modelScores=fit.scores();
                        var tickets=AiLabModel.tickets(modelScores,seed+":"+model);
                        var frequency=AiLabModel.tickets(AiLabModel.scores(history,category,"frequency"),seed+":frequency");
                        var random=AiLabModel.tickets(AiLabModel.scores(history,category,"random"),seed+":random");
                        Instant capturedAt=Instant.now();
                        if(!ForecastProtocol.target(category,capturedAt).toString().equals(target)) throw new IllegalStateException("CUTOFF_CHANGED");
                        List<Map<String,Object>> batch=new ArrayList<>();
                        for(int slot=0;slot<AiLabModel.BUDGET;slot++) {
                            var f=ForecastProtocol.prepare(history,category,"lab-"+model+"-"+slot,AiLabModel.VERSION,codeVersion,config,tickets.get(slot),capturedAt);
                            var explanation=PredictionExplanation.lab(fit,model);
                            explanation.put("selectionPolicy",slot==0?"top_six":"seeded_weighted_sample");
                            f.put("explanation",explanation);
                            f.put("labModel",model); f.put("slot",slot); f.put("frequencyNumbers",frequency.get(slot)); f.put("randomNumbers",random.get(slot)); batch.add(f);
                        }
                        if(!ForecastProtocol.target(category,Instant.now()).toString().equals(target)) throw new IllegalStateException("CUTOFF_CHANGED");
                        store.captureBatch(batch);
                        outcomes.add(map("category",category,"model",model,"status","SAVED"));
                    }
                } catch(Exception e) { outcomes.add(map("category",category,"status","NOT_SAVED","message",Objects.toString(e.getMessage(),"Error"))); }
            }
            store.reconcile(all,Instant.now());
            lastRun=map("status",enabled?"COMPLETED":"CAPTURE_DISABLED","checkedAt",Instant.now().toString(),"outcomes",outcomes);
            return lastRun;
        } finally { lock.unlock(); }
    }
    @Scheduled(initialDelay=20000,fixedDelay=60000)
    public void tick() {
        try { run(); } catch(Exception e) { lastRun=map("status","ERROR","checkedAt",Instant.now().toString(),"message",Objects.toString(e.getMessage(),"Error")); }
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> overview(String category,int page) {
        if(!Set.of("MEGA","POWER").contains(category)||page<0) throw new IllegalArgumentException("Invalid category or page");
        var ledger=store.ledger();
        var forecasts=((List<Map<String,Object>>)ledger.get("forecasts")).stream().filter(f->category.equals(f.get("category")))
                .sorted(Comparator.<Map<String,Object>,String>comparing(f->(String)f.get("targetDate")).reversed().thenComparing(f->(String)f.get("id"))).toList();
        var events=(List<Map<String,Object>>)ledger.get("settlements"); Map<Object,Map<String,Object>> latest=new HashMap<>(); events.forEach(e->latest.put(e.get("forecastId"),e));
        var rows=forecasts.stream().skip((long)page*50).limit(50).map(f->map("id",f.get("id"),"model",f.get("labModel"),"targetDate",f.get("targetDate"),"numbers",f.get("numbers"),
                "createdAt",f.get("createdAt"),"settlement",latest.get(f.get("id")))).toList();
        return map("enabled",enabled,"lastRun",lastRun,"budget",AiLabModel.BUDGET,"page",page,"totalRows",forecasts.size(),"rows",rows,"groups",evaluate(forecasts,events));
    }
    public static List<Map<String,Object>> evaluate(List<Map<String,Object>> forecasts,List<Map<String,Object>> events) {
        Map<Object,Map<String,Object>> latest=new HashMap<>(); events.forEach(e->latest.put(e.get("forecastId"),e));
        Map<String,List<Map<String,Object>>> cohorts=new TreeMap<>();
        for(var f:forecasts) if(ForecastProtocol.isProspective(f)) {
            String key=f.get("category")+"|"+f.get("labModel")+"|"+f.get("modelVersion")+"|"+f.get("codeVersion")+"|"+f.get("configurationHash");
            cohorts.computeIfAbsent(key,k->new ArrayList<>()).add(f);
        }
        List<Map<String,Object>> reports=new ArrayList<>();
        for(var cohort:cohorts.entrySet()) {
            var first=cohort.getValue().get(0); Map<String,List<Map<String,Object>>> dates=new TreeMap<>();
            cohort.getValue().forEach(f->dates.computeIfAbsent((String)f.get("targetDate"),k->new ArrayList<>()).add(f));
            List<int[]> measured=new ArrayList<>(); List<String> measuredDates=new ArrayList<>(); int ticketHits=0;
            for(var date:dates.entrySet()) {
                var tickets=date.getValue();
                if(tickets.size()!=AiLabModel.BUDGET || tickets.stream().anyMatch(f->latest.get(f.get("id"))==null || !"SETTLED".equals(latest.get(f.get("id")).get("status")))) continue;
                int[] hits=new int[3];
                for(var f:tickets) {
                    var e=latest.get(f.get("id")); int matched=((Number)e.get("matched")).intValue();
                    if(matched>=3) {hits[0]=1;ticketHits++;}
                    if(((Number)e.get("frequencyMatched")).intValue()>=3) hits[1]=1;
                    if(((Number)e.get("randomMatched")).intValue()>=3) hits[2]=1;
                }
                measured.add(hits); measuredDates.add(date.getKey());
            }
            int n=measured.size(); var recent=measured.subList(Math.max(0,n-30),n); var prior=measured.subList(Math.max(0,n-60),Math.max(0,n-30));
            boolean continuous=n>=60;
            for(int i=Math.max(1,n-59);i<n;i++) if(!TemporalRelationshipModel.scheduledAfter(LocalDate.parse(measuredDates.get(i-1)),(String)first.get("category")).equals(LocalDate.parse(measuredDates.get(i)))) continuous=false;
            boolean candidate=continuous && rate(recent,0)>=50 && rate(prior,0)>=50 && lowerDifference(measured,1)>0 && lowerDifference(measured,2)>0;
            reports.add(map("cohort",cohort.getKey(),"model",first.get("labModel"),"modelVersion",first.get("modelVersion"),"savedDraws",dates.size(),"evaluatedDraws",n,
                    "ticketHit3Percent",n==0?null:100.0*ticketHits/(n*AiLabModel.BUDGET),"drawHit3Percent",rate(measured,0),"frequencyHit3Percent",rate(measured,1),"randomHit3Percent",rate(measured,2),
                    "recent30Percent",rate(recent,0),"previous30Percent",rate(prior,0),"continuous60",continuous,"frequencyDifferenceLower95",n<2?null:lowerDifference(measured,1),
                    "randomDifferenceLower95",n<2?null:lowerDifference(measured,2),"status",candidate?"VALIDATION_CANDIDATE":"COLLECTING",
                    "firstTargetDate",dates.keySet().iterator().next(),"lastTargetDate",new ArrayList<>(dates.keySet()).get(dates.size()-1)));
        }
        return reports;
    }
    private static Double rate(List<int[]> rows,int index) { return rows.isEmpty()?null:100.0*rows.stream().mapToInt(r->r[index]).sum()/rows.size(); }
    private static double lowerDifference(List<int[]> rows,int control) {
        if(rows.size()<2) return Double.NEGATIVE_INFINITY;
        double mean=rows.stream().mapToDouble(r->r[0]-r[control]).average().orElse(0);
        double variance=rows.stream().mapToDouble(r->Math.pow(r[0]-r[control]-mean,2)).sum()/(rows.size()-1);
        return 100*(mean-1.96*Math.sqrt(variance/rows.size()));
    }
}
