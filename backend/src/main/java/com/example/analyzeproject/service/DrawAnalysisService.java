package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.DrawReport;
import com.example.analyzeproject.dto.DrawReport.*;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

/** Read-only reporting. No dependency on a prediction service or on the active configuration. */
@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class DrawAnalysisService {
    private final LotteryNumberRepository draws;
    private final UserTicketRepository users;
    private final ForecastStore forecasts;
    private final AiLabStore lab;
    private final WorkspaceStore workspace;
    private final Clock clock;
    private static final Set<String> ALGORITHMS=Set.of("temporal_learning","bayesian_graph","deep_stacking","combined_ensemble","lab-logistic","lab-recency");
    @Autowired public DrawAnalysisService(LotteryNumberRepository draws,UserTicketRepository users,ForecastStore forecasts,AiLabStore lab,WorkspaceStore workspace) {
        this(draws,users,forecasts,lab,workspace,Clock.systemUTC());
    }
    public DrawAnalysisService(LotteryNumberRepository draws,UserTicketRepository users,ForecastStore forecasts,AiLabStore lab,WorkspaceStore workspace,Clock clock) {
        this.draws=draws; this.users=users; this.forecasts=forecasts; this.lab=lab; this.workspace=workspace; this.clock=clock;
    }
    public DrawReport report(String category,String requestedDate,String algorithm,String evidenceId) {
        WorkspaceProtocol.category(category);
        if(!ALGORITHMS.contains(Objects.toString(algorithm,""))) throw new IllegalArgumentException("Thuật toán không hợp lệ.");
        Instant now=clock.instant();
        var raw=draws.findByCategoryOrderByDrawDateDescCreatedAtDesc(category);
        var history=ForecastProtocol.training(raw,category,now);
        LocalDate date=requestedDate==null || requestedDate.isBlank()
                ? history.stream().map(LotteryNumber::getDrawDate).max(Comparator.naturalOrder()).orElse(null) : date(requestedDate);
        var context=new Context(category,date==null?null:date.toString(),algorithm,now.toString());
        if(date==null) return empty(context,"NO_HISTORY","Chưa có kết quả đủ điều kiện.");
        if(now.isBefore(date.atTime(19,0).atZone(ForecastProtocol.ZONE).toInstant())) return empty(context,"PENDING","Chưa đến mốc tiếp nhận kết quả 19:00 giờ Việt Nam.");
        var actual=history.stream().filter(r->date.equals(r.getDrawDate())).findFirst().orElse(null);
        if(actual==null) return empty(context,raw.stream().anyMatch(r->date.equals(r.getDrawDate()))?"RESULT_UNAVAILABLE":"DRAW_NOT_FOUND","Chưa có kết quả hợp lệ cho kỳ đã chọn.");
        var saved=forecasts.forecasts(); var labs=lab.snapshots();
        List<Evidence> evidence=new ArrayList<>();
        for(var f:saved) if(matches(f,category,date) && algorithm.equals(f.get("algorithm")) && validSnapshot(f)) evidence.add(evidence(f,"FORECAST",algorithm,string(f,"id"),null));
        for(var f:labs) if(matches(f,category,date) && algorithm.equals("lab-"+f.get("labModel")) && validSnapshot(f)) evidence.add(evidence(f,"AI_LAB",algorithm,string(f,"id"),null));
        var suggestions=workspace.suggestionsForDraw(category,date.toString());
        for(var s:suggestions) if(validSuggestion(s)) {
            for(String slot:List.of("primary","secondary","combined")) {
                String a=slot.equals("primary")?string(s,"primaryAlgorithm"):slot.equals("secondary")?string(s,"secondaryAlgorithm"):"combined_ensemble";
                if(!algorithm.equals(a)) continue;
                var ticket=maps(s.get("tickets")).stream().filter(t->slot.equals(t.get("id"))).findFirst().orElse(null);
                if(ticket==null) continue;
                var view=new LinkedHashMap<>(s); view.put("numbers",ticket.get("numbers"));
                view.put("modelVersion",s.get(slot+"ModelVersion"));
                view.put("explanation",object(s.get(slot+"Explanation")));
                // Older workspace records do have a frozen ranking, even when scores were not saved.
                List<Integer> ranking=integers(s.get(slot+"Ranking"));
                evidence.add(evidence(view,"WORKSPACE_SUGGESTION",algorithm,"workspace:"+s.get("id")+":"+slot,ranking));
            }
        }
        evidence.sort(Comparator.comparing(Evidence::createdAt,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Evidence::id));
        Evidence selected=evidenceId==null || evidenceId.isBlank()?evidence.stream().findFirst().orElse(null):evidence.stream().filter(e->e.id().equals(evidenceId)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Bản dự đoán không thuộc loại/kỳ/thuật toán đang xem."));
        var details=selected==null?missingDetails(category):selected.numberDetails();
        List<String> warnings=new ArrayList<>(); List<TicketGroup> groups=new ArrayList<>();
        var checked=users.findAllByOrderByCheckedAtDesc().stream().filter(t->category.equals(t.getCategory())&&date.toString().equals(t.getDrawDate())).toList();
        List<TicketResult> checkedResults=new ArrayList<>(); int invalid=0;
        for(var t:checked) {
            try { checkedResults.add(TicketAnalysis.evaluate(category,t.getNumbers(),actual.getNumbers(),actual.getSpecialNumber(),"checked:"+t.getId(),"CHECKED_AFTER_DRAW",null,"Vé đã dò #"+t.getId(),null,false)); }
            catch(IllegalArgumentException e) { invalid++; }
        }
        if(!checked.isEmpty()) groups.add(group("checked","Vé đã nhập để dò","CHECKED_AFTER_DRAW",checkedResults,null,null,invalid));
        var batches=maps(workspace.history(category).get("batches"));
        for(var batch:batches) {
            if(!Boolean.TRUE.equals(batch.get("active")) || !date.toString().equals(batch.get("targetDate")) || !beforeCutoff(batch.get("committedAt"),date)) continue;
            var tickets=readTickets(batch.get("tickets"),actual,"WORKSPACE_COMMIT",string(batch,"id"));
            var freq=control(batch.get("frequencyTickets"),actual); var rand=control(batch.get("randomTickets"),actual);
            var g=group("batch:"+batch.get("id"),"Vé đã chốt · revision "+batch.get("revision"),"COMMITTED_BEFORE_DRAW",tickets,freq,rand,maps(batch.get("tickets")).size()-tickets.size());
            var paired=tickets.stream().filter(t->t.originalNumbers()!=null && t.originalNumbers().size()==6).toList();
            List<TicketResult> originals=new ArrayList<>(); List<TicketResult> edited=new ArrayList<>();
            for(var t:paired) {
                try {
                    originals.add(TicketAnalysis.evaluate(category,t.originalNumbers(),actual.getNumbers(),actual.getSpecialNumber(),t.id(),t.source(),t.sourceId(),t.label(),null,false));
                    edited.add(t);
                } catch(IllegalArgumentException ignored) { /* Invalid legacy original cannot form a pair. */ }
            }
            double originalMean=originals.stream().mapToInt(TicketResult::matchedCount).average().orElse(0), finalMean=edited.stream().mapToInt(TicketResult::matchedCount).average().orElse(0);
            groups.add(new TicketGroup(g.id(),g.label(),g.timing(),g.tickets(),g.portfolio(),g.frequency(),g.random(),g.invalidTicketCount(),
                    originals.isEmpty()?null:new PairComparison(originals.size(),originalMean,finalMean,finalMean-originalMean)));
        }
        for(var e:evidence) {
            var t=TicketAnalysis.evaluate(category,e.numbers(),actual.getNumbers(),actual.getSpecialNumber(),e.id(),e.source(),e.id(),e.algorithm(),null,false);
            groups.add(group(e.id(),"Bản gợi ý · "+e.algorithm(),"SAVED_BEFORE_DRAW",List.of(t),null,null,0));
        }
        if(invalid>0) warnings.add(invalid+" vé lịch sử sai định dạng được giữ nguyên và loại khỏi thống kê.");
        if(evidence.isEmpty()) warnings.add("Không tìm thấy snapshot hợp lệ trước kỳ cho thuật toán này. Không dựng lại điểm lịch sử.");
        warnings.add("Số khớp và độ phủ là quan sát kết quả; chưa chứng minh cải thiện khả năng dự đoán.");
        var evaluations=new ArrayList<>(evaluate(saved,labs,history,category,algorithm,date));
        evaluations.addAll(evaluateWorkspace(batches,history,category,date));
        var q=actual.getQuality();
        return new DrawReport("draw-report-v1",context,"SUCCESS",null,new Draw(actual.getNumbers().stream().sorted().toList(),actual.getSpecialNumber(),DrawQuality.fingerprint(actual),string(q,"status"),string(q,"sourceUrl"),Objects.toString(actual.getCreatedAt(),null)),
                evidence,selected==null?null:selected.id(),details,groups,evaluations,warnings);
    }
    public TicketGroup evaluateTickets(EvaluateRequest request) {
        if(request==null || request.tickets()==null || request.tickets().isEmpty() || request.tickets().size()>25) throw new IllegalArgumentException("Cần từ 1 đến 25 vé.");
        String category=WorkspaceProtocol.category(request.category()); LocalDate date=date(request.date());
        request.tickets().forEach(t->WorkspaceProtocol.numbers(category,t));
        var actual=ForecastProtocol.training(draws.findByCategoryOrderByDrawDateDescCreatedAtDesc(category),category,clock.instant()).stream().filter(r->date.equals(r.getDrawDate())).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Chưa có kết quả hợp lệ để dò vé."));
        List<TicketResult> results=new ArrayList<>();
        for(int i=0;i<request.tickets().size();i++) results.add(TicketAnalysis.evaluate(category,request.tickets().get(i),actual.getNumbers(),actual.getSpecialNumber(),"manual:"+i,"MANUAL_CHECK",null,"Vé nhập #"+(i+1),null,false));
        return group("manual","Vé nhập để đối chiếu · chưa lưu","CHECKED_AFTER_DRAW",results,null,null,0);
    }
    private DrawReport empty(Context c,String status,String message) {
        return new DrawReport("draw-report-v1",c,status,message,null,List.of(),null,missingDetails(c.category()),List.of(),List.of(),List.of());
    }
    private static LocalDate date(String value) {
        try { return LocalDate.parse(value); } catch(Exception e) { throw new IllegalArgumentException("Ngày kỳ quay không hợp lệ."); }
    }
    private static TicketGroup group(String id,String label,String timing,List<TicketResult> tickets,Portfolio freq,Portfolio random,int invalid) {
        var p=TicketAnalysis.portfolio(tickets);
        return new TicketGroup(id,label,timing,tickets,p,freq!=null&&freq.ticketCount()==p.ticketCount()?freq:null,random!=null&&random.ticketCount()==p.ticketCount()?random:null,invalid,null);
    }
    private static boolean matches(Map<String,Object> value,String category,LocalDate date) { return category.equals(value.get("category"))&&date.toString().equals(value.get("targetDate")); }
    private static boolean beforeCutoff(Object at,LocalDate target) {
        try { return Instant.parse(at.toString()).isBefore(target.atTime(18,0).atZone(ForecastProtocol.ZONE).toInstant()); } catch(Exception e) { return false; }
    }
    private static boolean validSnapshot(Map<String,Object> f) {
        try {
            WorkspaceProtocol.numbers(string(f,"category"),integers(f.get("numbers")));
            var history=maps(f.get("history"));
            return ForecastProtocol.isProspective(f) && !history.isEmpty()
                    && Objects.equals(f.get("trainingThrough"),history.stream().map(r->string(r,"drawDate")).max(Comparator.naturalOrder()).orElse(null))
                    && Objects.equals(f.get("trainingThrough"),ForecastProtocol.previous(string(f,"category"),date(string(f,"targetDate"))).toString());
        } catch(Exception e) { return false; }
    }
    private static boolean validSuggestion(Map<String,Object> s) {
        try {
            var copy=new LinkedHashMap<>(s); copy.put("protocol",ForecastProtocol.VERSION);
            copy.put("configurationHash",ForecastProtocol.hash(s.get("configuration")));
            return "READY".equals(s.get("status"))&&ForecastProtocol.isProspective(copy)
                    && Objects.equals(s.get("trainingThrough"),ForecastProtocol.previous(string(s,"category"),date(string(s,"targetDate"))).toString());
        } catch(Exception e) { return false; }
    }
    private static Evidence evidence(Map<String,Object> f,String source,String algorithm,String id,List<Integer> fallbackRanking) {
        String category=string(f,"category"); int max="POWER".equals(category)?55:45;
        var explanation=object(f.get("explanation")); List<Integer> ranking=integers(explanation.get("ranking"));
        if(ranking.isEmpty() && fallbackRanking!=null) ranking=fallbackRanking;
        boolean full=ranking.size()==max&&new HashSet<>(ranking).size()==max&&ranking.stream().allMatch(n->n>=1&&n<=max);
        Map<Integer,Map<String,Object>> saved=new HashMap<>();
        for(var d:maps(explanation.get("details"))) if(d.get("number") instanceof Number n) saved.put(n.intValue(),d);
        var history=maps(f.get("history")); List<NumberDetail> details=new ArrayList<>();
        int[] frequencies=new int[max+1],gaps=new int[max+1]; Arrays.fill(gaps,history.size());
        var ordered=history.stream().sorted(Comparator.comparing(r->string(r,"drawDate"),Comparator.reverseOrder())).toList();
        for(int i=0;i<ordered.size();i++) for(int n:integers(ordered.get(i).get("numbers"))) {
            if(n>=1&&n<=max) { frequencies[n]++; gaps[n]=Math.min(gaps[n],i); }
        }
        for(int n=1;n<=max;n++) {
            var d=saved.getOrDefault(n,Map.of());
            Integer frequency=history.isEmpty()?null:frequencies[n], gap=history.isEmpty()||frequencies[n]==0?null:gaps[n];
            Double score=full?finite(d.get("score")):null;
            details.add(new NumberDetail(n,full?ranking.indexOf(n)+1:null,score,score==null?null:string(explanation,"scoreType"),frequency,gap,id,
                    full?"Xếp hạng đã lưu trước kỳ; điểm nội bộ không phải xác suất trúng.":"Chưa lưu xếp hạng/điểm. Tần suất và khoảng cách chỉ mô tả lịch sử đầu vào đã lưu.",numericMap(d.get("features"))));
        }
        return new Evidence(id,source,algorithm,string(f,"createdAt"),string(f,"modelVersion"),string(f,"codeVersion"),
                f.get("configurationHash")==null?ForecastProtocol.hash(f.get("configuration")):string(f,"configurationHash"),string(f,"trainingThrough"),string(f,"historyHash"),
                WorkspaceProtocol.numbers(category,integers(f.get("numbers"))),full?(saved.isEmpty()?"RANKING_ONLY":"AVAILABLE"):"NOT_SAVED",details,
                Objects.toString(explanation.get("selectionPolicy"),algorithm.equals("combined_ensemble")?"constrained_rank_fusion":
                        f.get("slot") instanceof Number slot&&slot.intValue()>0?"seeded_weighted_sample":"top_six"),
                ForecastProtocol.map("primaryWeight",algorithm.equals("combined_ensemble")?explanation.getOrDefault("primaryWeight",f.get("primaryWeight")):null,
                        "keep",explanation.getOrDefault("keep",f.getOrDefault("keep",List.of())),"exclude",explanation.getOrDefault("exclude",f.getOrDefault("exclude",List.of()))),history.size(),
                string(explanation,"scoringPolicy"),numericMap(explanation.get("modelWeights")));
    }
    private static List<NumberDetail> missingDetails(String category) {
        return IntStream.rangeClosed(1,"POWER".equals(category)?55:45).mapToObj(n->new NumberDetail(n,null,null,null,null,null,null,"Chưa có snapshot trước kỳ.",Map.of())).toList();
    }
    private static List<TicketResult> readTickets(Object values,LotteryNumber actual,String source,String sourceId) {
        List<TicketResult> results=new ArrayList<>(); int i=0;
        for(var t:maps(values)) {
            try { results.add(TicketAnalysis.evaluate(actual.getCategory(),integers(t.get("numbers")),actual.getNumbers(),actual.getSpecialNumber(),sourceId+":"+(i++),source,
                    t.get("sourceId")==null?null:sourceId+":"+t.get("sourceId"),Objects.toString(t.get("label"),source),t.get("originalNumbers")==null?null:integers(t.get("originalNumbers")),Boolean.TRUE.equals(t.get("edited")))); }
            catch(IllegalArgumentException ignored) { /* Old invalid records remain stored and are counted separately. */ }
        }
        return results;
    }
    private static Portfolio control(Object value,LotteryNumber actual) {
        if(!(value instanceof List<?> list) || list.isEmpty()) return null;
        List<TicketResult> tickets=new ArrayList<>();
        try { for(Object t:list) tickets.add(TicketAnalysis.evaluate(actual.getCategory(),integers(t),actual.getNumbers(),actual.getSpecialNumber(),"","CONTROL",null,"",null,false)); }
        catch(IllegalArgumentException e) { return null; }
        return TicketAnalysis.portfolio(tickets);
    }
    /** Only frozen, prospective model tickets; no reconstructed historical forecasts. */
    static List<Evaluation> evaluate(List<Map<String,Object>> saved,List<Map<String,Object>> labs,List<LotteryNumber> history,String category,String algorithm,LocalDate through) {
        Map<LocalDate,LotteryNumber> actuals=new HashMap<>(); history.forEach(r->actuals.put(r.getDrawDate(),r));
        Map<String,List<Map<String,Object>>> cohorts=new TreeMap<>();
        for(var source:List.of(saved,labs)) for(var f:source) {
            String a=f.containsKey("labModel")?"lab-"+f.get("labModel"):string(f,"algorithm");
            if(!category.equals(f.get("category")) || !algorithm.equals(a) || !validSnapshot(f)) continue;
            String key=(f.containsKey("labModel")?"AI_LAB":"FORECAST")+"|"+a+"|"+f.get("modelVersion")+"|"+f.get("codeVersion")+"|"+f.get("configurationHash");
            cohorts.computeIfAbsent(key,k->new ArrayList<>()).add(f);
        }
        List<Evaluation> result=new ArrayList<>();
        for(var entry:cohorts.entrySet()) {
            var first=entry.getValue().get(0); boolean isLab=first.containsKey("labModel"); int budget=isLab?AiLabModel.BUDGET:1;
            Map<String,List<Map<String,Object>>> dates=new TreeMap<>(); entry.getValue().forEach(f->dates.computeIfAbsent(string(f,"targetDate"),k->new ArrayList<>()).add(f));
            List<Portfolio> model=new ArrayList<>(),frequency=new ArrayList<>(),random=new ArrayList<>(); List<Double> differences=new ArrayList<>(); List<String> measured=new ArrayList<>();
            for(var day:dates.entrySet()) {
                LocalDate d=date(day.getKey()); var actual=actuals.get(d); var batch=day.getValue();
                if(d.isAfter(through)||actual==null||batch.size()!=budget||batch.stream().map(f->integers(f.get("numbers"))).distinct().count()!=budget) continue;
                if(isLab && (!new HashSet<>(batch.stream().map(f->f.get("slot")).toList()).equals(new HashSet<>(IntStream.range(0,budget).boxed().toList()))
                        || batch.stream().map(f->f.get("historyHash")).distinct().count()!=1)) continue;
                var p=control(batch.stream().map(f->f.get("numbers")).toList(),actual);
                if(p==null) continue;
                model.add(p); measured.add(d.toString());
                var f=control(batch.stream().map(v->v.get("frequencyNumbers")).toList(),actual);
                var r=control(batch.stream().map(v->v.get("randomNumbers")).toList(),actual);
                if(f!=null&&r!=null&&f.ticketCount()==budget&&r.ticketCount()==budget) { frequency.add(f);random.add(r);differences.add(p.averageMatched()-f.averageMatched()); }
            }
            result.add(summarize(entry.getKey(),isLab?"AI_LAB":"FORECAST",algorithm,string(first,"modelVersion"),budget,model,frequency,random,differences,measured));
        }
        return result;
    }
    private static List<Evaluation> evaluateWorkspace(List<Map<String,Object>> batches,List<LotteryNumber> history,String category,LocalDate through) {
        Map<LocalDate,LotteryNumber> actuals=new HashMap<>(); history.forEach(r->actuals.put(r.getDrawDate(),r));
        Map<String,List<Map<String,Object>>> cohorts=new TreeMap<>();
        for(var batch:batches) {
            if(!Boolean.TRUE.equals(batch.get("active")) || !category.equals(batch.get("category"))) continue;
            var d=date(string(batch,"targetDate")); if(d.isAfter(through)||!actuals.containsKey(d)||!beforeCutoff(batch.get("committedAt"),d)) continue;
            String key="WORKSPACE_USER|"+batch.get("protocol")+"|"+batch.get("codeVersion")+"|budget="+maps(batch.get("tickets")).size();
            cohorts.computeIfAbsent(key,k->new ArrayList<>()).add(batch);
        }
        List<Evaluation> reports=new ArrayList<>();
        for(var entry:cohorts.entrySet()) {
            List<Portfolio> models=new ArrayList<>(),frequency=new ArrayList<>(),random=new ArrayList<>();
            List<Double> differences=new ArrayList<>();List<String> dates=new ArrayList<>();int budget=0;
            var ordered=entry.getValue().stream().sorted(Comparator.comparing(b->string(b,"targetDate"))).toList();
            for(var batch:ordered) {
                var actual=actuals.get(date(string(batch,"targetDate")));var tickets=readTickets(batch.get("tickets"),actual,"WORKSPACE_COMMIT",string(batch,"id"));
                if(tickets.isEmpty()||tickets.size()!=maps(batch.get("tickets")).size()) continue;
                var p=TicketAnalysis.portfolio(tickets);if(p.duplicateTicketCount()!=0) continue;budget=p.ticketCount();
                models.add(p);dates.add(string(batch,"targetDate"));
                var f=control(batch.get("frequencyTickets"),actual);var r=control(batch.get("randomTickets"),actual);
                if(f!=null&&r!=null&&f.ticketCount()==budget&&r.ticketCount()==budget) { frequency.add(f);random.add(r);differences.add(p.averageMatched()-f.averageMatched()); }
            }
            reports.add(summarize(entry.getKey(),"WORKSPACE_USER","Lựa chọn người dùng",WorkspaceProtocol.VERSION,budget,models,frequency,random,differences,dates));
        }
        return reports;
    }
    private static Evaluation summarize(String key,String source,String algorithm,String version,int budget,List<Portfolio> model,List<Portfolio> frequency,List<Portfolio> random,List<Double> differences,List<String> measured) {
        int n=model.size(),paired=differences.size();double delta=differences.stream().mapToDouble(v->v).average().orElse(0);
        double margin=paired<2?0:1.96*Math.sqrt(differences.stream().mapToDouble(v->Math.pow(v-delta,2)).sum()/(paired-1)/paired);
        return new Evaluation(key,source,algorithm,version,budget,n,measured.isEmpty()?null:measured.get(0),measured.isEmpty()?null:measured.get(n-1),avg(model,0),avg(model,1),avg(model,2),
                n==0?null:100.0*model.stream().filter(p->p.bestMatched()>=3).count()/n,n==0?null:100.0*model.stream().filter(p->p.bestMatched()>=4).count()/n,
                avg(frequency,0),avg(random,0),paired,paired==0?null:delta,paired<2?null:List.of(delta-margin,delta+margin),n<30?"INSUFFICIENT_SAMPLE":"DESCRIPTIVE_ONLY");
    }
    private static Double avg(List<Portfolio> values,int metric) { return values.isEmpty()?null:values.stream().mapToDouble(p->metric==0?p.averageMatched():metric==1?p.bestMatched():p.coverageCount()).average().orElseThrow(); }
    private static Double finite(Object v) { return v instanceof Number n&&Double.isFinite(n.doubleValue())?n.doubleValue():null; }
    private static Map<String,Double> numericMap(Object value) {
        Map<String,Double> result=new TreeMap<>();object(value).forEach((key,v)->{ Double n=finite(v);if(n!=null) result.put(key,n); });return result;
    }
    static String string(Map<String,Object> v,String key) { return Objects.toString(v.get(key),null); }
    @SuppressWarnings("unchecked") static Map<String,Object> object(Object v) { return v instanceof Map<?,?>?(Map<String,Object>)v:Map.of(); }
    static List<Map<String,Object>> maps(Object v) { return v instanceof List<?> list?list.stream().filter(Map.class::isInstance).map(DrawAnalysisService::object).toList():List.of(); }
    static List<Integer> integers(Object v) {
        if(!(v instanceof List<?> list)) return List.of();
        if(list.stream().anyMatch(n->!(n instanceof Number x)||!Double.isFinite(x.doubleValue())||x.doubleValue()!=x.intValue())) return List.of();
        return list.stream().map(n->((Number)n).intValue()).toList();
    }
}
