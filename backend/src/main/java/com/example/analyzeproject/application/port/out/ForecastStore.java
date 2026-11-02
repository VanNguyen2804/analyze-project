package com.example.analyzeproject.application.port.out;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.application.dto.WorkspaceRequests.Commit;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public interface ForecastStore {
    List<Map<String,Object>> forecasts();
    List<Map<String,Object>> settlements();
    Map<String,Object> readLedger();
    Map<String,Object> capture(Map<String,Object> forecast);
    Map<String,Object> reconcile(List<LotteryNumber> rows, Instant now);
}
