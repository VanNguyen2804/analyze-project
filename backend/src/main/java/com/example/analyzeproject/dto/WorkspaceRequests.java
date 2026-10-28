package com.example.analyzeproject.dto;
import java.util.List;

public final class WorkspaceRequests {
    private WorkspaceRequests() {}
    public record Create(String category, String algorithm, String algorithm2) {}
    public record Combine(Double primaryWeight, List<Integer> keep, List<Integer> exclude) {}
    public record Ticket(String sourceId, List<Integer> numbers) {}
    public record Commit(String category, String targetDate, String suggestionId, int expectedRevision, String requestKey, List<Ticket> tickets, int labCount) {}
}
