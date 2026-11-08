package com.example.analyzeproject.application.port.out;

import java.time.Instant;
import java.util.Map;

/** A bounded queue; completion is fenced by the claim token, including after a process restart. */
public interface PageJobStore {
    record Job(String id, String status, Object data, String message, Instant updatedAt) {}
    record Work(String id, String kind, Map<String,Object> input, String token) {}
    Job enqueue(String key, String kind, Map<String,Object> input, int cacheSeconds, Instant now);
    Job find(String id);
    Work claim(Instant now);
    void complete(Work work, Object result, Instant now);
    void fail(Work work, Instant now);
    void heartbeat(Work work, Instant now);
    default String revision() { return "local"; }
}
