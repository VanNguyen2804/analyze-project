package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.domain.service.*;
import java.time.*;
import java.util.*;
import static com.example.analyzeproject.application.service.ForecastProtocol.map;

/** Converts frozen prospective features and currently valid results into bounded feedback replay. */
public final class AiLabFeedback {
    private AiLabFeedback() {}
    public record Evidence(List<AiLabModel.FeedbackRound> rounds, Map<String,Object> audit) {}

    @SuppressWarnings("unchecked")
    public static Evidence collect(List<Map<String,Object>> snapshots, List<LotteryNumber> history,
                                   String category, LocalDate target, Instant now, int maximumDraws) {
        Map<String,List<Map<String,Object>>> batches = new TreeMap<>();
        for (var f : snapshots) {
            if (!category.equals(f.get("category")) || !"logistic".equals(f.get("labModel"))
                    || !("ai-lab-logistic-v1".equals(f.get("modelVersion")) || AiLabModel.VERSION.equals(f.get("modelVersion"))) || !ForecastProtocol.isProspective(f)) continue;
            String date = (String) f.get("targetDate");
            if (!LocalDate.parse(date).isBefore(target) || now.isBefore(LocalDate.parse(date).atTime(19,0).atZone(ForecastProtocol.ZONE).toInstant())) continue;
            batches.computeIfAbsent(date, k -> new ArrayList<>()).add(f);
        }
        List<AiLabModel.FeedbackRound> rounds = new ArrayList<>();
        List<Map<String,Object>> lineage = new ArrayList<>(); int[] histogram = new int[7];
        var entries = new ArrayList<>(batches.entrySet());
        for (var batch : entries.subList(Math.max(0, entries.size() - maximumDraws), entries.size())) {
            var tickets = batch.getValue();
            if (tickets.size() != AiLabModel.BUDGET) continue;
            try {
                Set<Object> ids = new HashSet<>(), slots = new HashSet<>(); Set<Integer> selected = new HashSet<>();
                int max = "POWER".equals(category) ? 55 : 45;
                Map<String,Object> first = tickets.get(0);
                if (!PredictionExplanation.VERSION.equals(((Map<String,Object>) first.get("explanation")).get("schemaVersion"))) throw new IllegalArgumentException();
                var details = (List<Map<String,Object>>) ((Map<String,Object>) first.get("explanation")).get("details");
                String featureHash = ForecastProtocol.hash(details);
                Set<Integer> actual = null; int[] batchHistogram = new int[7];List<Map<String,Object>> batchLineage = new ArrayList<>();
                for (var f : tickets) {
                    if (!ids.add(f.get("id")) || !(f.get("slot") instanceof Number slot) || slot.intValue() < 0 || slot.intValue() >= AiLabModel.BUDGET || !slots.add(slot.intValue())) throw new IllegalArgumentException();
                    for (String key : List.of("modelVersion","codeVersion","configurationHash","historyHash"))
                        if (!Objects.equals(first.get(key), f.get(key))) throw new IllegalArgumentException();
                    if (!featureHash.equals(ForecastProtocol.hash(((Map<String,Object>) f.get("explanation")).get("details")))) throw new IllegalArgumentException();
                    List<Integer> numbers = ((List<Number>) f.get("numbers")).stream().map(Number::intValue).toList();
                    if (numbers.size() != 6 || new HashSet<>(numbers).size() != 6 || numbers.stream().anyMatch(n -> n < 1 || n > max)) throw new IllegalArgumentException();
                    // Re-settle against current quality-filtered history, so corrected or withdrawn results are respected.
                    var settlement = ForecastProtocol.settle(f, history, now, null);
                    if (!"SETTLED".equals(settlement.get("status"))) throw new IllegalArgumentException();
                    actual = new HashSet<>(((List<Number>) settlement.get("actualNumbers")).stream().map(Number::intValue).toList());
                    selected.addAll(numbers); batchHistogram[((Number) settlement.get("matched")).intValue()]++;
                    batchLineage.add(map("snapshotId",f.get("id"),"numbers",numbers,"historyHash",f.get("historyHash"),"resultFingerprint",settlement.get("resultFingerprint"),"featureHash",featureHash));
                }
                if (details.size() != max) throw new IllegalArgumentException();
                List<AiLabModel.FeedbackExample> examples = new ArrayList<>(); Set<Integer> seen = new HashSet<>();
                for (var detail : details) {
                    int number = ((Number) detail.get("number")).intValue();
                    if (number < 1 || number > max || !seen.add(number)) throw new IllegalArgumentException();
                    var features = (Map<String,Number>) detail.get("features");
                    double[] x = new double[5]; int j = 0;
                    for (String name : List.of("intercept","frequency","recent10","decay10","gap30")) x[j++] = features.get(name).doubleValue();
                    if (x[0] != 1 || Arrays.stream(x).anyMatch(v -> !Double.isFinite(v) || v < 0 || v > 1)) throw new IllegalArgumentException();
                    examples.add(new AiLabModel.FeedbackExample(number,x,selected.contains(number),actual.contains(number)));
                }
                examples.sort(Comparator.comparingInt(AiLabModel.FeedbackExample::number));
                rounds.add(new AiLabModel.FeedbackRound(batch.getKey(),examples));
                batchLineage.sort(Comparator.comparing(row -> row.get("snapshotId").toString()));lineage.addAll(batchLineage);
                for (int i = 0; i < histogram.length; i++) histogram[i] += batchHistogram[i];
            } catch (RuntimeException ignored) { /* Legacy snapshots without complete frozen features are not fabricated. */ }
        }
        return new Evidence(List.copyOf(rounds),map("policy","dense-signed-feedback-v1","draws",rounds.size(),
                "tickets",rounds.size()*AiLabModel.BUDGET,"ticketMatchHistogram",Arrays.stream(histogram).boxed().toList(),
                "sourceLineage",lineage,"dataHash",ForecastProtocol.hash(lineage),
                "description","Học từng số đúng/sai ở mọi vé, gồm 0, 1, 2 và 3+ số; mỗi số chỉ cập nhật một lần mỗi kỳ. Không cộng số khớp qua các kỳ thành vé trúng."));
    }
}
