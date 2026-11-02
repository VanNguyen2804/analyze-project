package com.example.analyzeproject.application.port.out;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.application.dto.WorkspaceRequests.Commit;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public interface MegaPortfolioStore {
    Map<String,Object> find(LocalDate target);
    Map<String,Object> find(LocalDate target,String version);
    Map<String,Object> capture(Map<String,Object> snapshot);
    Map<String,Object> ledger();
    void reconcile(List<LotteryNumber> draws,Instant now);
}
