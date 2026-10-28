package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.*;
import com.example.analyzeproject.dto.WorkspaceRequests.*;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static com.example.analyzeproject.service.ForecastProtocol.map;

@Service
public class WorkspaceService {
    public static final String LAB_POLICY="model-slot-round-robin-v1";
    private final AnalyzeService analyze;
    private final LotteryNumberRepository draws;
    private final AiLabStore lab;
    private final WorkspaceStore store;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public WorkspaceService(AnalyzeService analyze,LotteryNumberRepository draws,AiLabStore lab,WorkspaceStore store) {
        this(analyze,draws,lab,store,Clock.systemUTC());
    }
    public WorkspaceService(AnalyzeService analyze,LotteryNumberRepository draws,AiLabStore lab,WorkspaceStore store,Clock clock) {
        this.analyze=analyze; this.draws=draws; this.lab=lab; this.store=store; this.clock=clock;
    }
    private void algorithm(String id) {
        if(id==null || analyze.getAlgorithmCatalog().stream().noneMatch(a->id.equals(a.get("id")))) throw new IllegalArgumentException("Thuật toán không hợp lệ.");
    }
    private List<LotteryNumber> training(String category,Instant now) { return ForecastProtocol.training(draws.findByCategoryOrderByDrawDateDescCreatedAtDesc(category),category,now); }
    public PredictionResponseDto create(Create request) {
        String category=WorkspaceProtocol.category(request.category()); algorithm(request.algorithm()); algorithm(request.algorithm2());
        Instant now=clock.instant(); LocalDate target=ForecastProtocol.target(category,now); var history=training(category,now);
        boolean ready=WorkspaceProtocol.ready(history,category,target);
        var active=analyze.getLatestHyperparameter(category);
        PredictionResponseDto response=ready ? analyze.analyzeAndPredictWithConfiguration(category,request.algorithm(),request.algorithm2(),history,active) : new PredictionResponseDto();
        List<Map<String,Object>> tickets=new ArrayList<>();
        if(ready) {
            tickets.add(ticket("primary","ALGORITHM",request.algorithm(),response.getPrimaryAlgorithmResult()));
            tickets.add(ticket("secondary","ALGORITHM",request.algorithm2(),response.getSecondaryAlgorithmResult()));
            tickets.add(ticket("combined","COMBINED",request.algorithm()+" + "+request.algorithm2(),response.getCombinedAlgorithmResult()));
        }
        var labs=labSuggestions(category,target.toString(),history);
        @SuppressWarnings("unchecked") var labTickets=(List<Map<String,Object>>)labs.get("tickets"); tickets.addAll(labTickets);
        if(!ForecastProtocol.target(category,clock.instant()).equals(target)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Kỳ mục tiêu vừa thay đổi. Hãy tải lại.");
        var suggestion=map("id",UUID.randomUUID().toString(),"category",category,"targetDate",target.toString(),"createdAt",now.toString(),
                "status",ready?"READY":"WAITING_HISTORY","message",ready?null:"Đang chờ đủ lịch sử và kết quả kỳ liền trước. Bạn vẫn có thể tự nhập vé.",
                "trainingThrough",history.isEmpty()?null:history.get(history.size()-1).getDrawDate().toString(),"historyCount",history.size(),"historyHash",WorkspaceProtocol.historyHash(history),
                "history",savedHistory(history),"configuration",active==null?Map.of():map("version",active.getVersion(),"hyperparametersJson",active.getHyperparametersJson()),
                "codeVersion",WorkspaceProtocol.codeVersion(),"primaryAlgorithm",request.algorithm(),"secondaryAlgorithm",request.algorithm2(),
                "primaryRanking",ready?response.getPrimaryAlgorithmResult().get("ranking"):List.of(),"secondaryRanking",ready?response.getSecondaryAlgorithmResult().get("ranking"):List.of(),
                "primaryWeight",0.5,"keep",List.of(),"exclude",List.of(),"labPolicy",LAB_POLICY,"labStatus",labs.get("status"),"tickets",tickets);
        store.saveSuggestion(suggestion);
        response.setStatus(ready?"SUCCESS":"WAITING"); response.setCategory(category); response.setTargetDrawDate(target.toString());
        response.setWorkspace(publicView(suggestion)); return response;
    }
    private Map<String,Object> ticket(String id,String source,String label,Map<String,Object> result) {
        return map("id",id,"source",source,"label",label,"numbers",result.get("numbers"));
    }
    public static List<Map<String,Object>> savedHistory(List<LotteryNumber> history) {
        return history.stream().map(r->map("category",r.getCategory(),"drawDate",r.getDrawDate().toString(),"numbers",r.getNumbers(),"specialNumber",r.getSpecialNumber(),"quality",r.getQuality())).toList();
    }
    public Map<String,Object> publicView(Map<String,Object> source) {
        var copy=new LinkedHashMap<>(source); for(String key:List.of("history","configuration","primaryRanking","secondaryRanking")) copy.remove(key);
        copy.put("expectedRevision",store.revision((String)source.get("category"),(String)source.get("targetDate"))); return copy;
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> combine(String id,Combine request) {
        var original=store.suggestion(id); ensureCurrent(original,true);
        if(request.primaryWeight()==null) throw new IllegalArgumentException("Thiếu trọng số kết hợp.");
        var numbers=WorkspaceProtocol.combine((String)original.get("category"),(List<Integer>)original.get("primaryRanking"),(List<Integer>)original.get("secondaryRanking"),request.primaryWeight(),request.keep(),request.exclude());
        var variant=new LinkedHashMap<>(original); variant.put("id",UUID.randomUUID().toString()); variant.put("parentId",id); variant.put("createdAt",clock.instant().toString());
        variant.put("sourceCodeVersion",original.get("codeVersion")); variant.put("codeVersion",WorkspaceProtocol.codeVersion());
        variant.put("primaryWeight",request.primaryWeight()); variant.put("keep",request.keep()==null?List.of():request.keep()); variant.put("exclude",request.exclude()==null?List.of():request.exclude());
        var tickets=((List<Map<String,Object>>)original.get("tickets")).stream().map(t->{ var copy=new LinkedHashMap<>(t); if("combined".equals(t.get("id"))) copy.put("numbers",numbers); return (Map<String,Object>)copy; }).toList();
        variant.put("tickets",tickets); store.saveSuggestion(variant); return publicView(variant);
    }
    private void ensureCurrent(Map<String,Object> suggestion,boolean sourced) {
        String category=(String)suggestion.get("category"); LocalDate date=LocalDate.parse((String)suggestion.get("targetDate")); Instant now=clock.instant();
        if(!ForecastProtocol.target(category,now).equals(date)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Kỳ đã đổi hoặc hết giờ chốt. Hãy tải lại.");
        if(sourced) {
            var current=training(category,now);
            if(!WorkspaceProtocol.ready(current,category,date) || !WorkspaceProtocol.historyHash(current).equals(suggestion.get("historyHash")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Lịch sử nguồn đã đổi hoặc chưa đủ mới. Hãy tải lại gợi ý; vé của bạn được giữ trong bản nháp.");
        }
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> commit(Commit request) {
        WorkspaceProtocol.category(request.category());
        try { LocalDate.parse(request.targetDate()); } catch(Exception e) { throw new IllegalArgumentException("Ngày kỳ quay không hợp lệ."); }
        if(request.requestKey()==null || request.requestKey().length()>80 || request.requestKey().isBlank() || request.expectedRevision()<0) throw new IllegalArgumentException("Thông tin chốt vé không hợp lệ.");
        if(request.tickets()==null || request.tickets().isEmpty() || request.tickets().size()>25) throw new IllegalArgumentException("Chọn từ 1 đến 25 vé.");
        if(request.tickets().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Thiếu thông tin một vé.");
        if(request.labCount()<3 || request.labCount()>5) throw new IllegalArgumentException("Số dãy AI Lab phải từ 3 đến 5.");
        return store.commit(request,()->{
            var suggestion=store.suggestion(request.suggestionId());
            if(!request.category().equals(suggestion.get("category")) || !request.targetDate().equals(suggestion.get("targetDate"))) throw new IllegalArgumentException("Vé không khớp loại/kỳ của bản gợi ý.");
            boolean sourced=request.tickets().stream().anyMatch(t->t.sourceId()!=null && !t.sourceId().isBlank()); ensureCurrent(suggestion,sourced);
            Map<Object,Map<String,Object>> sources=new HashMap<>(); ((List<Map<String,Object>>)suggestion.get("tickets")).forEach(t->sources.put(t.get("id"),t));
            List<Map<String,Object>> tickets=new ArrayList<>(); Set<List<Integer>> unique=new HashSet<>();
            for(var selected:request.tickets()) {
                var nums=WorkspaceProtocol.numbers(request.category(),selected.numbers());
                if(!unique.add(nums)) throw new IllegalArgumentException("Danh sách đang có vé trùng nhau.");
                var origin=selected.sourceId()==null||selected.sourceId().isBlank()?null:sources.get(selected.sourceId());
                if(selected.sourceId()!=null && !selected.sourceId().isBlank() && origin==null) throw new IllegalArgumentException("Nguồn vé không thuộc bản gợi ý đang chốt.");
                tickets.add(map("numbers",nums,"sourceId",origin==null?null:origin.get("id"),"source",origin==null?"MANUAL":origin.get("source"),
                        "label",origin==null?"Tự nhập":origin.get("label"),"originalNumbers",origin==null?null:origin.get("numbers"),"edited",origin!=null&&!nums.equals(origin.get("numbers")),"labOrigins",origin==null?null:origin.get("labOrigins")));
            }
            var history=training(request.category(),clock.instant()); String seed=request.category()+":"+request.targetDate()+":"+tickets.size();
            if(sourced && !WorkspaceProtocol.historyHash(history).equals(suggestion.get("historyHash"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Lịch sử vừa đổi. Hãy tải lại gợi ý.");
            return map("id",UUID.randomUUID().toString(),"category",request.category(),"targetDate",request.targetDate(),"suggestionId",request.suggestionId(),
                    "protocol",WorkspaceProtocol.VERSION,"codeVersion",suggestion.get("codeVersion"),"suggestion",publicView(suggestion),"tickets",tickets,"labCount",request.labCount(),
                    "historyHash",WorkspaceProtocol.historyHash(history),"frequencyTickets",WorkspaceProtocol.controls(history,request.category(),tickets.size(),seed,true),
                    "randomTickets",WorkspaceProtocol.controls(history,request.category(),tickets.size(),seed,false));
        });
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> labSuggestions(String category,String target,List<LotteryNumber> history) {
        WorkspaceProtocol.category(category); LocalDate date=LocalDate.parse(target);
        if(!WorkspaceProtocol.ready(history,category,date)) return map("status","WAITING_HISTORY","policy",LAB_POLICY,"tickets",List.of());
        List<Map<String,Object>> snapshots;
        try { snapshots=lab.snapshots(); } catch(Exception e) { return map("status","UNAVAILABLE","policy",LAB_POLICY,"tickets",List.of()); }
        Map<String,List<Map<String,Object>>> models=new LinkedHashMap<>();
        for(String model:AiLabModel.MODELS) {
            var batch=snapshots.stream().filter(f->category.equals(f.get("category"))&&target.equals(f.get("targetDate"))&&model.equals(f.get("labModel")))
                    .sorted(Comparator.comparingInt(f->((Number)f.get("slot")).intValue())).toList();
            boolean valid=batch.size()==AiLabModel.BUDGET && batch.stream().map(f->((Number)f.get("slot")).intValue()).collect(java.util.stream.Collectors.toSet()).equals(Set.of(0,1,2,3,4))
                    && batch.stream().map(f->f.get("codeVersion")).distinct().count()==1 && batch.stream().map(f->f.get("configurationHash")).distinct().count()==1
                    && batch.stream().map(f->f.get("modelVersion")).distinct().count()==1
                    && batch.stream().allMatch(f->ForecastProtocol.isProspective(f)&&history.get(history.size()-1).getDrawDate().toString().equals(f.get("trainingThrough"))&&WorkspaceProtocol.historyHash(history).equals(f.get("historyHash")));
            if(valid) try { batch.forEach(f->WorkspaceProtocol.numbers(category,(List<Integer>)f.get("numbers"))); models.put(model,batch); } catch(IllegalArgumentException ignored) { /* invalid batch omitted */ }
        }
        Map<List<Integer>,Map<String,Object>> unique=new LinkedHashMap<>();
        for(int slot=0;slot<AiLabModel.BUDGET;slot++) for(var model:models.entrySet()) {
            var f=model.getValue().get(slot); var nums=((List<Integer>)f.get("numbers")).stream().sorted().toList();
            var origin=map("id",f.get("id"),"model",f.get("labModel"),"modelVersion",f.get("modelVersion"),"codeVersion",f.get("codeVersion"),"createdAt",f.get("createdAt"),"trainingThrough",f.get("trainingThrough"));
            var item=unique.get(nums);
            if(item==null) { item=map("id","lab:"+f.get("id"),"source","AI_LAB","label",("logistic".equals(f.get("labModel"))?"Logistic Regression":"Tần suất suy giảm")+" · vé "+(slot+1),"numbers",nums,"labOrigins",new ArrayList<Map<String,Object>>()); unique.put(nums,item); }
            ((List<Map<String,Object>>)item.get("labOrigins")).add(origin);
        }
        return map("status",unique.size()>=3?"READY":"PARTIAL","policy",LAB_POLICY,"tickets",unique.values().stream().limit(5).toList());
    }
    public Map<String,Object> labSuggestions(String category,String target) { return labSuggestions(category,target,training(category,clock.instant())); }
    public Map<String,Object> history(String category) { return store.history(WorkspaceProtocol.category(category)); }
    public Map<String,Object> reconcile() { store.reconcile(draws.findAllByOrderByDrawDateDescCreatedAtDesc(),Instant.now()); return map("status","COMPLETED"); }
    @Scheduled(initialDelay=25000,fixedDelay=60000)
    public void tick() { try { reconcile(); } catch(Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Workspace reconciliation failed: {}",e.getClass().getSimpleName()); } }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> settle(Map<String,Object> batch,List<LotteryNumber> rows,Instant now,Map<String,Object> previous) {
        var date=LocalDate.parse((String)batch.get("targetDate"));
        var actual=DrawQuality.history(rows).stream().filter(r->batch.get("category").equals(r.getCategory())&&date.equals(r.getDrawDate())).findFirst().orElse(null);
        boolean ready=actual!=null&&!now.isBefore(date.atTime(19,0).atZone(ForecastProtocol.ZONE).toInstant());
        var result=map("batchId",batch.get("id"),"checkedAt",now.toString(),"status",ready?"SETTLED":previous!=null&&Set.of("SETTLED","RESULT_UNAVAILABLE").contains(previous.get("status"))?"RESULT_UNAVAILABLE":"PENDING",
                "resultFingerprint",ready?DrawQuality.fingerprint(actual):null,"actualNumbers",ready?actual.getNumbers().stream().sorted().toList():null);
        if(!ready) return result;
        var tickets=(List<Map<String,Object>>)batch.get("tickets");
        result.put("user",WorkspaceProtocol.metrics(tickets.stream().map(t->(List<Integer>)t.get("numbers")).toList(),actual.getNumbers()));
        var frequency=(List<List<Integer>>)batch.get("frequencyTickets");
        result.put("frequency",frequency.isEmpty()?null:WorkspaceProtocol.metrics(frequency,actual.getNumbers()));
        result.put("random",WorkspaceProtocol.metrics((List<List<Integer>>)batch.get("randomTickets"),actual.getNumbers()));
        var paired=tickets.stream().filter(t->t.get("originalNumbers")!=null).toList();
        result.put("originalSelected",WorkspaceProtocol.metrics(paired.stream().map(t->(List<Integer>)t.get("originalNumbers")).toList(),actual.getNumbers()));
        result.put("editedSelected",WorkspaceProtocol.metrics(paired.stream().map(t->(List<Integer>)t.get("numbers")).toList(),actual.getNumbers()));
        var suggestion=(Map<String,Object>)batch.get("suggestion"); var offered=(List<Map<String,Object>>)suggestion.get("tickets");
        result.put("algorithmSuggestions",WorkspaceProtocol.metrics(offered.stream().filter(t->!"AI_LAB".equals(t.get("source"))).map(t->(List<Integer>)t.get("numbers")).distinct().toList(),actual.getNumbers()));
        result.put("labSuggestions",WorkspaceProtocol.metrics(offered.stream().filter(t->"AI_LAB".equals(t.get("source"))).limit(((Number)batch.getOrDefault("labCount",5)).intValue()).map(t->(List<Integer>)t.get("numbers")).toList(),actual.getNumbers()));
        return result;
    }
}
