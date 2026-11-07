package com.example.analyzeproject.application.port.out;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.application.dto.WorkspaceRequests.Commit;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public interface AiLabStore {
    default Set<String> snapshotIds() {
        return snapshots().stream().map(f->Objects.toString(f.get("id"))).collect(java.util.stream.Collectors.toSet());
    }
    default List<Map<String,Object>> feedbackSnapshots(String category,LocalDate target,int maximumDraws,Instant now) {
        var candidates=snapshots().stream().filter(f->com.example.analyzeproject.application.service.AiLabFeedback.eligible(f,category,target,now))
                .sorted(Comparator.<Map<String,Object>,String>comparing(f->Objects.toString(f.get("targetDate"))).reversed()).toList();
        Set<Object> dates=new LinkedHashSet<>();List<Map<String,Object>> result=new ArrayList<>();
        for(var f:candidates){if(!dates.contains(f.get("targetDate"))&&dates.size()>=maximumDraws)break;dates.add(f.get("targetDate"));result.add(f);}return result;
    }
    default com.example.analyzeproject.application.dto.AiLabReadView readOverview(String category) {
        return com.example.analyzeproject.application.dto.AiLabReadView.fromLedger(ledger(),category);
    }
    List<Map<String,Object>> snapshots();
    Map<String,Object> ledger();
    void captureBatch(List<Map<String,Object>> snapshots);
    void reconcile(List<LotteryNumber> rows,Instant now);
}
