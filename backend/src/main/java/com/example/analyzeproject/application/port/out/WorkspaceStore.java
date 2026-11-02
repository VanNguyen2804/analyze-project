package com.example.analyzeproject.application.port.out;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.application.dto.WorkspaceRequests.Commit;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public interface WorkspaceStore {
    void saveSuggestion(Map<String,Object> value);
    Map<String,Object> suggestion(String id);
    List<Map<String,Object>> suggestionsForDraw(String category, String date);
    int revision(String category,String date);
    Map<String,Object> commit(Commit request, Supplier<Map<String,Object>> build);
    Map<String,Object> history(String category);
    void reconcile(List<LotteryNumber> rows,Instant now);
}
