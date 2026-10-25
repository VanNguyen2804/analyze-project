package com.example.analyzeproject.service;

import com.example.analyzeproject.model.LotteryNumber;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Sequentially fits relationship experts; each evaluation precedes observing its target. */
final class TemporalRelationshipModel {
    private static final String[] IDS = {"frequency", "lag1", "lag2", "repeat", "neighbor", "same_tail", "special_to_main"};
    private static final String[] NAMES = {"Tần suất nền", "Chuyển tiếp một kỳ", "Chuyển tiếp hai kỳ", "Lặp số", "Lân cận ±1", "Cùng đuôi", "Số phụ sang số chính"};
    record Result(double[] scores, Map<String, Object> analysis) {}

    private final int max;
    private final String category;
    private final double base, prior, rate;
    private final int minHistory;
    private final int[] frequency;
    private final int[][][] counts;
    private final int[][] exposures, successes = new int[4][2], trials = new int[4][2];
    private double[] weights = new double[IDS.length];
    private int seen, evaluated, skipped, matched, baselineMatched, candidateMatched, fallbackDraws, hit3;
    private double modelBrier, baselineBrier;

    private TemporalRelationshipModel(String category, double prior, double rate, int minHistory) {
        this.category = category;
        max = "POWER".equals(category) ? 55 : 45;
        base = 6.0 / max;
        this.prior = valid(prior, max / 6.0, 0.01, 10000);
        this.rate = valid(rate, 0.5, 0, 10);
        this.minHistory = (int) valid(minHistory, 5, 1, 1000);
        frequency = new int[max + 1];
        counts = new int[2][max + 1][max + 1];
        exposures = new int[2][max + 1];
        Arrays.fill(weights, 1.0 / IDS.length);
    }

    static Result fit(List<LotteryNumber> history, String category) {
        return fit(history, category, Double.NaN, 0.5, 5);
    }

    static Result fit(List<LotteryNumber> history, String category, double prior, double rate, int minHistory) {
        TemporalRelationshipModel model = new TemporalRelationshipModel(category, prior, rate, minHistory);
        Set<LocalDate> seenDates = new HashSet<>();
        List<LotteryNumber> draws = DrawQuality.history(history).stream()
                .filter(r -> category.equals(r.getCategory()) && r.getDrawDate() != null && r.getNumbers() != null
                        && r.getNumbers().size() == 6 && new HashSet<>(r.getNumbers()).size() == 6
                        && r.getNumbers().stream().allMatch(n -> n != null && n >= 1 && n <= model.max))
                .sorted(Comparator.comparing(LotteryNumber::getDrawDate).reversed()
                        .thenComparing(LotteryNumber::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LotteryNumber::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(r -> seenDates.add(r.getDrawDate())).collect(Collectors.toList());
        Collections.reverse(draws);
        return model.train(draws);
    }

    private static double valid(double value, double fallback, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max ? value : fallback;
    }

    static LocalDate scheduledAfter(LocalDate date, String category) {
        Set<DayOfWeek> days = "POWER".equals(category)
                ? Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
                : Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY);
        do { date = date.plusDays(1); } while (!days.contains(date.getDayOfWeek()));
        return date;
    }

    private boolean adjacent(List<LotteryNumber> draws, int i) {
        return i > 0 && scheduledAfter(draws.get(i - 1).getDrawDate(), category).equals(draws.get(i).getDrawDate());
    }

    private boolean[] flags(int n, LotteryNumber previous) {
        if (previous == null) return new boolean[4];
        return new boolean[]{previous.getNumbers().contains(n), previous.getNumbers().stream().anyMatch(p -> Math.abs(p - n) == 1),
                previous.getNumbers().stream().anyMatch(p -> p != n && p % 10 == n % 10), Objects.equals(previous.getSpecialNumber(), n)};
    }

    private double[] normalize(double[] scores) {
        double total = Arrays.stream(scores).skip(1).sum();
        for (int n = 1; n <= max; n++) scores[n] = Math.min(0.999, Math.max(0.001, scores[n] * 6 / total));
        return scores;
    }

    private double[][] experts(LotteryNumber previous, LotteryNumber older) {
        double[][] result = new double[IDS.length][max + 1];
        for (int n = 1; n <= max; n++) {
            double marginal = (frequency[n] + prior * base) / (seen + prior);
            result[0][n] = marginal;
            for (int lag = 0; lag < 2; lag++) {
                LotteryNumber source = lag == 0 ? previous : older;
                double sum = 0;
                if (source != null) for (int seed : source.getNumbers()) sum += (counts[lag][seed][n] + prior * marginal) / (exposures[lag][seed] + prior);
                result[lag + 1][n] = source == null ? marginal : sum / 6;
            }
            boolean[] flags = flags(n, previous);
            for (int feature = 0; feature < 4; feature++) {
                int state = flags[feature] ? 1 : 0;
                result[feature + 3][n] = feature == 3 && (previous == null || previous.getSpecialNumber() == null) ? marginal
                        : (successes[feature][state] + prior * marginal) / (trials[feature][state] + prior);
            }
        }
        for (double[] scores : result) normalize(scores);
        return result;
    }

    private double[] mix(double[][] models) {
        double[] result = new double[max + 1];
        for (int n = 1; n <= max; n++) for (int i = 0; i < IDS.length; i++) result[n] += models[i][n] * weights[i];
        return normalize(result);
    }

    private List<Integer> rank(double[] scores) {
        return java.util.stream.IntStream.rangeClosed(1, max).boxed()
                .sorted(Comparator.<Integer>comparingDouble(n -> scores[n]).reversed().thenComparingInt(n -> n)).collect(Collectors.toList());
    }

    private double brier(double[] scores, Set<Integer> actual) {
        double loss = 0;
        for (int n = 1; n <= max; n++) loss += Math.pow(scores[n] - (actual.contains(n) ? 1 : 0), 2);
        return loss / max;
    }

    private Result train(List<LotteryNumber> draws) {
        for (int t = 0; t < draws.size(); t++) {
            LotteryNumber draw = draws.get(t);
            Set<Integer> actual = new HashSet<>(draw.getNumbers());
            boolean connected = adjacent(draws, t);
            double[][] models = experts(connected ? draws.get(t - 1) : null, connected && adjacent(draws, t - 1) ? draws.get(t - 2) : null);
            if (seen >= minHistory && connected) {
                double[] candidate = mix(models);
                boolean useBaseline = evaluated > 0 && candidateMatched < baselineMatched;
                double[] forecast = useBaseline ? models[0] : candidate;
                int hits = (int) rank(forecast).stream().limit(6).filter(actual::contains).count();
                candidateMatched += (int) rank(candidate).stream().limit(6).filter(actual::contains).count();
                if (useBaseline) fallbackDraws++;
                evaluated++; matched += hits;
                baselineMatched += (int) rank(models[0]).stream().limit(6).filter(actual::contains).count();
                if (hits >= 3) hit3++;
                modelBrier += brier(forecast, actual); baselineBrier += brier(models[0], actual);
                double total = 0;
                for (int i = 0; i < weights.length; i++) { weights[i] *= Math.exp(-rate * max * brier(models[i], actual)); total += weights[i]; }
                for (int i = 0; i < weights.length; i++) weights[i] /= total;
            }
            if (t > 0 && !connected) skipped++;
            if (connected) {
                for (int lag = 0; lag < 2; lag++) {
                    if (lag == 1 && !adjacent(draws, t - 1)) continue;
                    for (int source : draws.get(t - lag - 1).getNumbers()) {
                        exposures[lag][source]++;
                        for (int target : draw.getNumbers()) counts[lag][source][target]++;
                    }
                }
                for (int n = 1; n <= max; n++) {
                    boolean[] flags = flags(n, draws.get(t - 1));
                    for (int feature = 0; feature < 4; feature++) {
                        if (feature == 3 && draws.get(t - 1).getSpecialNumber() == null) continue;
                        int state = flags[feature] ? 1 : 0;
                        trials[feature][state]++; if (actual.contains(n)) successes[feature][state]++;
                    }
                }
            }
            for (int n : draw.getNumbers()) frequency[n]++;
            seen++;
        }
        LotteryNumber last = draws.isEmpty() ? null : draws.get(draws.size() - 1);
        double[][] finalExperts = experts(last, adjacent(draws, draws.size() - 1) ? draws.get(draws.size() - 2) : null);
        boolean useBaseline = evaluated > 0 && candidateMatched < baselineMatched;
        double[] scores = useBaseline ? finalExperts[0] : mix(finalExperts);
        List<Map<String, Object>> transitions = new ArrayList<>();
        if (last != null) for (int source : last.getNumbers()) for (int target = 1; target <= max; target++) {
            if (counts[0][source][target] > 0) transitions.add(map("fromNumber", source, "toNumber", target,
                    "support", exposures[0][source], "occurrences", counts[0][source][target], "conditionalPercent",
                    100 * (counts[0][source][target] + prior * (frequency[target] + prior * base) / (seen + prior)) / (exposures[0][source] + prior)));
        }
        transitions.sort(Comparator.<Map<String, Object>>comparingDouble(m -> (double) m.get("conditionalPercent")).reversed()
                .thenComparing(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("support")).reversed())
                .thenComparingInt(m -> (int) m.get("fromNumber")).thenComparingInt(m -> (int) m.get("toNumber")));
        List<Map<String, Object>> patterns = new ArrayList<>();
        for (int i = 0; i < IDS.length; i++) patterns.add(map("id", IDS[i], "name", NAMES[i], "weightPercent", weights[i] * 100,
                "support", i < 3 ? (i == 0 ? seen : Arrays.stream(exposures[i - 1]).sum() / 6) : trials[i - 3][1],
                "occurrences", i < 3 ? null : successes[i - 3][1]));
        return new Result(scores, map("modelVersion", "temporal-links-v1", "category", category, "asOfDate", last == null ? null : last.getDrawDate().toString(),
                "forecastPolicy", useBaseline ? "frequency_fallback" : "learned_mixture",
                "trainingDraws", seen, "skippedGapTransitions", skipped, "parameters", map("priorStrength", prior, "learningRate", rate, "minHistory", minHistory),
                "patterns", patterns, "topTransitions", transitions.stream().limit(12).collect(Collectors.toList()),
                "predictedNumbers", seen == 0 ? List.of() : rank(scores).stream().limit(6).sorted().collect(Collectors.toList()),
                "evaluation", map("method", "prequential", "evaluatedDraws", evaluated, "minHistory", minHistory,
                        "averageMatched", evaluated == 0 ? null : (double) matched / evaluated,
                        "candidateAverageMatched", evaluated == 0 ? null : (double) candidateMatched / evaluated, "baselineFallbackDraws", fallbackDraws,
                        "baselineAverageMatched", evaluated == 0 ? null : (double) baselineMatched / evaluated,
                        "hit3RatePercent", evaluated == 0 ? null : 100.0 * hit3 / evaluated,
                        "brierScore", evaluated == 0 ? null : modelBrier / evaluated,
                        "baselineBrierScore", evaluated == 0 ? null : baselineBrier / evaluated)));
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
}
