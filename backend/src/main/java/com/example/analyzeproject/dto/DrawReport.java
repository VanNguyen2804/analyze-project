package com.example.analyzeproject.dto;

import java.util.List;
import java.util.Map;

/** The analysis contract deliberately separates observations from frozen model evidence. */
public record DrawReport(
        String schemaVersion, Context context, String status, String message, Draw draw,
        List<Evidence> evidence, String selectedEvidenceId, List<NumberDetail> numberDetails,
        List<TicketGroup> ticketGroups, List<Evaluation> evaluation, List<String> warnings) {
    public record Context(String category, String drawDate, String algorithm, String generatedAt) {}
    public record Draw(List<Integer> numbers, Integer specialNumber, String fingerprint, String quality,
                       String sourceUrl, String updatedAt) {}
    public record NumberDetail(int number, Integer rank, Double score, String scoreType,
                               Integer frequency, Integer drawGap, String evidenceId, String note, Map<String,Double> features) {}
    public record Evidence(String id, String source, String algorithm, String createdAt, String modelVersion,
                           String codeVersion, String configurationHash, String trainingThrough, String historyHash,
                           List<Integer> numbers, String explanationStatus, List<NumberDetail> numberDetails,
                           String selectionPolicy, Map<String,Object> selectionConstraints, int historyCount,
                           String scoringPolicy, Map<String,Double> modelWeights) {}
    public record TicketResult(String id, String source, String sourceId, String label, List<Integer> numbers,
                               List<Integer> originalNumbers, boolean edited, List<Integer> matchedNumbers,
                               List<Integer> missedNumbers, int matchedCount, boolean matchedSpecial, String prize) {}
    public record Portfolio(int enteredTicketCount, int ticketCount, int duplicateTicketCount, int uniqueNumberCount,
                            List<Integer> uniqueNumbers, int rawMatchedOccurrences, List<Integer> coverage,
                            int coverageCount, double coveragePercent, Integer bestMatched, Double averageMatched,
                            List<Integer> matchHistogram, List<List<Integer>> overlaps, Map<Integer, Integer> numberUsage) {}
    public record TicketGroup(String id, String label, String timing, List<TicketResult> tickets,
                              Portfolio portfolio, Portfolio frequency, Portfolio random, int invalidTicketCount,
                              PairComparison comparison) {}
    public record PairComparison(int pairedTicketCount, double originalAverageMatched, double finalAverageMatched,
                                 double averageDifference) {}
    public record Evaluation(String cohort, String source, String algorithm, String modelVersion,
                             int ticketBudget, int evaluatedDraws, String firstDate, String lastDate,
                             Double averageMatched, Double bestMatchedAverage, Double coverageAverage,
                             Double hit3RatePercent, Double hit4RatePercent, Double frequencyAverageMatched,
                             Double randomAverageMatched, int pairedDraws, Double pairedDifferenceVsFrequency,
                             List<Double> approximate95PercentInterval, String evidenceStatus) {}
    public record EvaluateRequest(String category, String date, List<List<Integer>> tickets) {}
}
