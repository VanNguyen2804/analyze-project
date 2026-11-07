package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.service.*;

import com.example.analyzeproject.application.port.out.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import static com.example.analyzeproject.application.service.ForecastProtocol.map;

public class AiLabService {
    private final AiLabStore store;
    private final LotteryNumberRepository draws;
    private final boolean enabled;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Map<String,Object> lastRun=map("status","NOT_RUN");
    private final String codeVersion;
    private final AiLabModel.FeedbackPolicy feedbackPolicy;
    private final Clock clock;
    private final boolean challengersEnabled;
    public AiLabService(AiLabStore store,LotteryNumberRepository draws,boolean enabled) {
        this(store,draws,enabled,AiLabModel.FeedbackPolicy.defaults());
    }
    public AiLabService(AiLabStore store,LotteryNumberRepository draws,boolean enabled,AiLabModel.FeedbackPolicy feedbackPolicy) {
        this(store,draws,enabled,feedbackPolicy,Clock.systemUTC());
    }
    public AiLabService(AiLabStore store,LotteryNumberRepository draws,boolean enabled,AiLabModel.FeedbackPolicy feedbackPolicy,Clock clock) {
        this(store,draws,enabled,feedbackPolicy,clock,true);
    }
    public AiLabService(AiLabStore store,LotteryNumberRepository draws,boolean enabled,AiLabModel.FeedbackPolicy feedbackPolicy,Clock clock,boolean challengersEnabled) {
        this.store=store; this.draws=draws; this.enabled=enabled;
        this.feedbackPolicy=feedbackPolicy;
        this.clock=clock;
        this.challengersEnabled=challengersEnabled;
        codeVersion=WorkspaceProtocol.fingerprint(AiLabModel.class,AiLabModel.TrainingPolicy.class,AiLabService.class,AiLabFeedback.class,AiLabProbability.class,ForecastProtocol.class,DrawQuality.class,CoveragePortfolio.class,CoveragePortfolio.Policy.class);
    }
    public Map<String,Object> run() {
        if(!lock.tryLock()) return map("status","BUSY");
        try {
            Instant now=clock.instant(); var all=draws.findAllByOrderByDrawDateDescCreatedAtDesc();
            var existing=store.snapshots(); Set<Object> ids=new HashSet<>(); existing.forEach(f->ids.add(f.get("id")));
            List<Map<String,Object>> outcomes=new ArrayList<>();
            if(enabled) for(String category:List.of("MEGA","POWER")) {
                try {
                    var history=ForecastProtocol.training(all,category,now);
                    if(history.size()<10) throw new IllegalStateException("HISTORY_NOT_READY: cần ít nhất 10 kỳ hợp lệ để huấn luyện");
                    String target=ForecastProtocol.target(category,now).toString();
                    for(String model:AiLabModel.MODELS) {
                        if(!challengersEnabled && Set.of("shrunk_frequency","logistic_coverage").contains(model)) continue;
                        String prefix=category+":"+target+":lab-"+model+"-";
                        if(ids.contains(prefix+0)) { outcomes.add(map("category",category,"model",model,"status","ALREADY_SAVED")); continue; }
                        var config=map("protocol",AiLabModel.VERSION,"budget",AiLabModel.BUDGET,"epochs",80,"learningRate",0.2,"l2",0.05,
                                "features",List.of("frequency","recent10","decay10","gap30"),"minimumDraws",60,"windowDraws",30,"minimumWindowHitRate",0.5);
                        config.put("feedbackPolicy","logistic".equals(model)?map("version","dense-signed-feedback-v1","learningRate",feedbackPolicy.learningRate(),"selectedExtraWeight",feedbackPolicy.selectedExtraWeight(),"maximumDraws",feedbackPolicy.maximumDraws()):map("version","not_applied"));
                        config.put("generatorPolicy", "logistic_coverage".equals(model) ? CoveragePortfolio.VERSION : "seeded-weighted-v1");
                        if ("logistic_coverage".equals(model)) config.put("coveragePolicy", CoveragePortfolio.Policy.defaults());
                        if ("shrunk_frequency".equals(model)) config.put("priorDraws", 16);
                        // Validate freshness before fitting; do not capture a partially trained or stale batch.
                        ForecastProtocol.prepare(history,category,"validation",AiLabModel.VERSION,codeVersion,config,List.of(1,2,3,4,5,6),now);
                        String seed=AiLabModel.VERSION+":"+category+":"+target;
                        var feedback=AiLabFeedback.collect(existing,history,category,LocalDate.parse(target),now,feedbackPolicy.maximumDraws());
                        var fit=AiLabModel.fit(history,category,model,feedback.rounds(),feedbackPolicy);var modelScores=fit.scores();
                        var tickets="logistic_coverage".equals(model)
                                ? CoveragePortfolio.select(PredictionExplanation.ranking(modelScores),modelScores.length-1,AiLabModel.BUDGET,seed,CoveragePortfolio.Policy.defaults())
                                : AiLabModel.tickets(modelScores,seed+":"+(model.startsWith("logistic")?"logistic":model));
                        var frequency=AiLabModel.tickets(AiLabModel.scores(history,category,"frequency"),seed+":frequency");
                        var random=AiLabModel.tickets(AiLabModel.scores(history,category,"random"),seed+":random");
                        Instant capturedAt=clock.instant();
                        if(!ForecastProtocol.target(category,capturedAt).toString().equals(target)) throw new IllegalStateException("CUTOFF_CHANGED");
                        List<Map<String,Object>> batch=new ArrayList<>();
                        for(int slot=0;slot<AiLabModel.BUDGET;slot++) {
                            var f=ForecastProtocol.prepare(history,category,"lab-"+model+"-"+slot,AiLabModel.VERSION,codeVersion,config,tickets.get(slot),capturedAt);
                            var explanation=PredictionExplanation.lab(fit,model);
                            if("logistic".equals(model))explanation.put("feedback",feedback.audit());
                            explanation.put("selectionPolicy","logistic_coverage".equals(model)?CoveragePortfolio.VERSION:slot==0?"top_six":"seeded_weighted_sample");
                            f.put("experimentId","vietlott-challengers-2026-11");
                            f.put("modelPolicyVersion",model+":v1");
                            f.put("evaluationVersion","lab-outcomes-v2");
                            f.put("explanation",explanation);
                            f.put("labModel",model); f.put("slot",slot); f.put("frequencyNumbers",frequency.get(slot)); f.put("randomNumbers",random.get(slot)); batch.add(f);
                        }
                        if(!ForecastProtocol.target(category,clock.instant()).toString().equals(target)) throw new IllegalStateException("CUTOFF_CHANGED");
                        store.captureBatch(batch);
                        outcomes.add(map("category",category,"model",model,"status","SAVED"));
                    }
                } catch(Exception e) { outcomes.add(map("category",category,"status","NOT_SAVED","message",Objects.toString(e.getMessage(),"Error"))); }
            }
            store.reconcile(all,clock.instant());
            lastRun=map("status",enabled?"COMPLETED":"CAPTURE_DISABLED","checkedAt",clock.instant().toString(),"outcomes",outcomes);
            return lastRun;
        } finally { lock.unlock(); }
    }
    public void tick() {
        try { run(); } catch(Exception e) { lastRun=map("status","ERROR","checkedAt",clock.instant().toString(),"message",Objects.toString(e.getMessage(),"Error")); }
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> overview(String category,int page) {
        if(!Set.of("MEGA","POWER").contains(category)||page<0) throw new IllegalArgumentException("Invalid category or page");
        var ledger=store.ledger();
        var forecasts=((List<Map<String,Object>>)ledger.get("forecasts")).stream().filter(f->category.equals(f.get("category")))
                .sorted(Comparator.<Map<String,Object>,String>comparing(f->(String)f.get("targetDate")).reversed().thenComparing(f->(String)f.get("id"))).toList();
        var events=(List<Map<String,Object>>)ledger.get("settlements"); Map<Object,Map<String,Object>> latest=new HashMap<>(); events.forEach(e->latest.put(e.get("forecastId"),e));
        var rows=forecasts.stream().skip((long)page*50).limit(50).map(f->map("id",f.get("id"),"model",f.get("labModel"),"targetDate",f.get("targetDate"),"numbers",f.get("numbers"),
                "createdAt",f.get("createdAt"),"feedback",feedbackSummary(f),"settlement",latest.get(f.get("id")))).toList();
        return map("enabled",enabled,"challengersEnabled",challengersEnabled,"lastRun",lastRun,"budget",AiLabModel.BUDGET,"page",page,"totalRows",forecasts.size(),"rows",rows,"groups",evaluate(forecasts,events));
    }
    private static Object feedbackSummary(Map<String,Object> forecast) {
        if (forecast.get("explanation") instanceof Map<?,?> explanation && explanation.get("feedback") instanceof Map<?,?> feedback)
            return map("draws",feedback.get("draws"),"tickets",feedback.get("tickets"),"ticketMatchHistogram",feedback.get("ticketMatchHistogram"));
        return null;
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
            List<AiLabProgress.Draw> progressDraws=new ArrayList<>();
            var probability = new AiLabProbability();
            for(var date:dates.entrySet()) {
                var tickets=date.getValue();
                if(!completeBatch(tickets) || !settledTogether(tickets,latest)) continue;
                probability.add(tickets.get(0),latest.get(tickets.get(0).get("id")));
                int[] hits=new int[3]; List<AiLabProgress.Ticket> progressTickets=new ArrayList<>();
                for(var f:tickets) {
                    var e=latest.get(f.get("id")); int matched=((Number)e.get("matched")).intValue();
                    progressTickets.add(new AiLabProgress.Ticket(matched,((Number)e.get("frequencyMatched")).intValue(),((Number)e.get("randomMatched")).intValue()));
                    if(matched>=3) {hits[0]=1;ticketHits++;}
                    if(((Number)e.get("frequencyMatched")).intValue()>=3) hits[1]=1;
                    if(((Number)e.get("randomMatched")).intValue()>=3) hits[2]=1;
                }
                measured.add(hits); measuredDates.add(date.getKey());
                progressDraws.add(new AiLabProgress.Draw(date.getKey(),progressTickets));
            }
            int n=measured.size(); var recent=measured.subList(Math.max(0,n-30),n); var prior=measured.subList(Math.max(0,n-60),Math.max(0,n-30));
            boolean continuous=n>=60;
            for(int i=Math.max(1,n-59);i<n;i++) if(!TemporalRelationshipModel.scheduledAfter(LocalDate.parse(measuredDates.get(i-1)),(String)first.get("category")).equals(LocalDate.parse(measuredDates.get(i)))) continuous=false;
            boolean candidate=!Set.of("shrunk_frequency","logistic_coverage").contains(first.get("labModel")) && continuous && rate(recent,0)>=50 && rate(prior,0)>=50 && lowerDifference(measured,1)>0 && lowerDifference(measured,2)>0;
            reports.add(map("cohort",cohort.getKey(),"model",first.get("labModel"),"modelVersion",first.get("modelVersion"),"savedDraws",dates.size(),"evaluatedDraws",n,
                    "budget",AiLabModel.BUDGET,"evidenceType","PROSPECTIVE_FROZEN","evaluationVersion","lab-outcomes-v2",
                    "experimentId",first.get("experimentId"),"modelPolicyVersion",first.get("modelPolicyVersion"),
                    "feedbackEnabled","logistic".equals(first.get("labModel")) && first.get("configuration") instanceof Map<?,?> configuration && configuration.get("feedbackPolicy") instanceof Map<?,?> policy && "dense-signed-feedback-v1".equals(policy.get("version")),
                    "probability",probability.report(),
                    "feedbackComparison",feedbackComparison(cohort.getValue(),forecasts,latest),
                    "candidatePolicy",map("minimumDraws",60,"windowDraws",30,"minimumWindowHitPercent",50,"automaticPromotion",false),
                    "progress",AiLabProgress.summarize(progressDraws),
                    "ticketHit3Percent",n==0?null:100.0*ticketHits/(n*AiLabModel.BUDGET),"drawHit3Percent",rate(measured,0),"frequencyHit3Percent",rate(measured,1),"randomHit3Percent",rate(measured,2),
                    "recent30Percent",rate(recent,0),"previous30Percent",rate(prior,0),"continuous60",continuous,"frequencyDifferenceLower95",n<2?null:lowerDifference(measured,1),
                    "randomDifferenceLower95",n<2?null:lowerDifference(measured,2),"status",candidate?"VALIDATION_CANDIDATE":"COLLECTING",
                    "firstTargetDate",dates.keySet().iterator().next(),"lastTargetDate",new ArrayList<>(dates.keySet()).get(dates.size()-1)));
        }
        return reports;
    }
    private static Map<String,Object> feedbackComparison(List<Map<String,Object>> cohort,
            List<Map<String,Object>> forecasts, Map<Object,Map<String,Object>> latest) {
        if (!"logistic".equals(cohort.get(0).get("labModel"))) return null;
        int draws=0, matches=0, controlMatches=0;
        var dates=new TreeMap<Object,List<Map<String,Object>>>();
        cohort.forEach(f->dates.computeIfAbsent(f.get("targetDate"),k->new ArrayList<>()).add(f));
        for (var batch:dates.values()) {
            var first=batch.get(0);
            var control=forecasts.stream().filter(f->"logistic_no_feedback".equals(f.get("labModel"))
                    && ForecastProtocol.isProspective(f)
                    && List.of("category","targetDate","modelVersion","codeVersion","historyHash").stream()
                        .allMatch(key->Objects.equals(first.get(key),f.get(key)))
                    && comparisonConfiguration(first).equals(comparisonConfiguration(f))).toList();
            if (!completeBatch(batch) || !completeBatch(control)) continue;
            var both=new ArrayList<>(batch); both.addAll(control);
            if (both.stream().anyMatch(f->latest.get(f.get("id"))==null
                    || !"SETTLED".equals(latest.get(f.get("id")).get("status")))) continue;
            Object actual=latest.get(first.get("id")).get("actualNumbers");
            if (actual==null || both.stream().anyMatch(f->!Objects.equals(actual,latest.get(f.get("id")).get("actualNumbers")))) continue;
            draws++;
            matches+=batch.stream().mapToInt(f->((Number)latest.get(f.get("id")).get("matched")).intValue()).sum();
            controlMatches+=control.stream().mapToInt(f->((Number)latest.get(f.get("id")).get("matched")).intValue()).sum();
        }
        return map("pairedDraws",draws,"averageMatched",draws==0?null:(double)matches/(draws*AiLabModel.BUDGET),
                "noFeedbackAverageMatched",draws==0?null:(double)controlMatches/(draws*AiLabModel.BUDGET),
                "difference",draws==0?null:(double)(matches-controlMatches)/(draws*AiLabModel.BUDGET));
    }
    private static Map<Object,Object> comparisonConfiguration(Map<String,Object> forecast) {
        Map<Object,Object> config=new LinkedHashMap<>();
        if (forecast.get("configuration") instanceof Map<?,?> values) config.putAll(values);
        config.remove("feedbackPolicy");
        return config;
    }
    static boolean completeBatch(List<Map<String,Object>> batch) {
        return batch.size()==AiLabModel.BUDGET
                && batch.stream().map(f->f.get("slot")).collect(java.util.stream.Collectors.toSet()).equals(Set.of(0,1,2,3,4))
                && batch.stream().map(f->f.get("numbers")).distinct().count()==AiLabModel.BUDGET
                && batch.stream().map(f->f.get("historyHash")).distinct().count()==1;
    }
    private static boolean settledTogether(List<Map<String,Object>> batch,Map<Object,Map<String,Object>> latest) {
        var first=latest.get(batch.get(0).get("id"));
        if(first==null || first.get("actualNumbers")==null) return false;
        return batch.stream().allMatch(f->{var e=latest.get(f.get("id"));return e!=null&&"SETTLED".equals(e.get("status"))
                && Objects.equals(first.get("actualNumbers"),e.get("actualNumbers"))&&Objects.equals(first.get("resultFingerprint"),e.get("resultFingerprint"));});
    }
    private static Double rate(List<int[]> rows,int index) { return rows.isEmpty()?null:100.0*rows.stream().mapToInt(r->r[index]).sum()/rows.size(); }
    private static double lowerDifference(List<int[]> rows,int control) {
        if(rows.size()<2) return Double.NEGATIVE_INFINITY;
        double mean=rows.stream().mapToDouble(r->r[0]-r[control]).average().orElse(0);
        double variance=rows.stream().mapToDouble(r->Math.pow(r[0]-r[control]-mean,2)).sum()/(rows.size()-1);
        return 100*(mean-1.96*Math.sqrt(variance/rows.size()));
    }
}
