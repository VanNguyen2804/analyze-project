package com.example.analyzeproject.application.port.out;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.application.dto.WorkspaceRequests.Commit;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public interface AiLabStore {
    List<Map<String,Object>> snapshots();
    Map<String,Object> ledger();
    void captureBatch(List<Map<String,Object>> snapshots);
    void reconcile(List<LotteryNumber> rows,Instant now);
}
