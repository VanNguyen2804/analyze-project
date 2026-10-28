package com.example.analyzeproject.service;

import com.example.analyzeproject.model.LotteryNumber;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.*;

/** Pure prospective evaluation. No fitting or retrospective ticket reconstruction. */
public final class ForecastProtocol {
    public static final String VERSION = "prospective-first-ticket-v1";
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final ObjectMapper JSON = new ObjectMapper();
    private ForecastProtocol() {}

    public static Map<String,Object> map(Object... entries) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
    public static String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(value))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String codeVersion() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Class<?> type : List.of(AnalyzeService.class, TemporalRelationshipModel.class, PredictionSupport.class, ForecastProtocol.class)) {
                try (var input = type.getResourceAsStream(type.getSimpleName() + ".class")) { digest.update(input.readAllBytes()); }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static LocalDate expected(String category, Instant now) {
        ZonedDateTime local = now.atZone(ZONE);
        LocalDate date = local.toLocalTime().isBefore(LocalTime.of(19,0)) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
        while (!scheduled(date, category)) date = date.minusDays(1);
        return date;
    }
    private static boolean scheduled(LocalDate date, String category) {
        return ("POWER".equals(category) ? Set.of(2,4,6) : Set.of(3,5,7)).contains(date.getDayOfWeek().getValue());
    }
    public static LocalDate target(String category, Instant now) {
        var local = now.atZone(ZONE);
        LocalDate date = local.toLocalDate();
        if (!local.toLocalTime().isBefore(LocalTime.of(18,0))) date = date.plusDays(1);
        while (!scheduled(date, category)) date = date.plusDays(1);
        return date;
    }
    public static LocalDate previous(String category, LocalDate target) {
        LocalDate date=target.minusDays(1);
        while(!scheduled(date,category)) date=date.minusDays(1);
        return date;
    }
    public static List<LotteryNumber> training(List<LotteryNumber> rows, String category, Instant now) {
        return DrawQuality.history(rows).stream().filter(r -> category.equals(r.getCategory()) && !r.getDrawDate().isAfter(expected(category, now)))
                .sorted(Comparator.comparing(LotteryNumber::getDrawDate)).toList();
    }
    public static Map<String,Object> prepare(List<LotteryNumber> rows, String category, String algorithm, String modelVersion,
            String codeVersion, Map<String,Object> configuration, List<Integer> numbers, Instant now) {
        if (!Set.of("MEGA","POWER").contains(category)) throw new IllegalArgumentException("Invalid category");
        var history = training(rows, category, now);
        if (history.size() < 5 || !history.get(history.size() - 1).getDrawDate().equals(expected(category, now)))
            throw new IllegalStateException("HISTORY_NOT_READY: cần ít nhất 5 kỳ và kỳ hoàn tất mới nhất");
        int max = "POWER".equals(category) ? 55 : 45;
        if (numbers == null || numbers.size() != 6 || new HashSet<>(numbers).size() != 6 || numbers.stream().anyMatch(n -> n == null || n < 1 || n > max))
            throw new IllegalArgumentException("Invalid six-number forecast");
        LocalDate target = target(category, now);
        if (!history.get(history.size()-1).getDrawDate().equals(previous(category,target)))
            throw new IllegalStateException("HISTORY_NOT_READY: đang chờ kết quả kỳ liền trước kỳ mục tiêu");
        if (history.stream().anyMatch(r -> !r.getDrawDate().isBefore(target))) throw new IllegalArgumentException("Future training data");
        int[] frequency = new int[max + 1];
        for (var row : history) for (int n : row.getNumbers()) frequency[n]++;
        List<Integer> baseline = IntStream.rangeClosed(1, max).boxed().sorted(Comparator.<Integer>comparingInt(n -> frequency[n]).reversed().thenComparingInt(n -> n)).limit(6).sorted().toList();
        List<Integer> pool = IntStream.rangeClosed(1,max).boxed().collect(Collectors.toCollection(ArrayList::new));
        // Fixed control seed uses only protocol/category/target. Same control across algorithms.
        String seed = VERSION + ":" + category + ":" + target;
        int state = 0x811c9dc5;
        for (int i = 0; i < seed.length(); i++) state = (state ^ seed.charAt(i)) * 16777619;
        for (int i = pool.size() - 1; i > 0; i--) {
            state += 0x6d2b79f5;
            int value = (state ^ (state >>> 15)) * (1 | state);
            value ^= value + (value ^ (value >>> 7)) * (61 | value);
            double random = Integer.toUnsignedLong(value ^ (value >>> 14)) / 4294967296.0;
            Collections.swap(pool, i, (int) Math.floor(random * (i + 1)));
        }
        List<Map<String,Object>> savedHistory = history.stream().map(r -> map("category", r.getCategory(), "drawDate", r.getDrawDate().toString(),
                "numbers", r.getNumbers().stream().sorted().toList(), "specialNumber", r.getSpecialNumber(), "quality", r.getQuality())).toList();
        return map("id", category + ":" + target + ":" + algorithm, "protocol", VERSION, "category", category, "targetDate", target.toString(),
                "createdAt", now.toString(), "algorithm", algorithm, "modelVersion", modelVersion, "codeVersion", codeVersion,
                "configuration", configuration, "configurationHash", hash(configuration), "trainingThrough", history.get(history.size()-1).getDrawDate().toString(),
                "historyHash", hash(history.stream().map(DrawQuality::fingerprint).toList()), "history", savedHistory,
                "numbers", numbers.stream().sorted().toList(), "frequencyNumbers", baseline, "randomNumbers", pool.subList(0,6).stream().sorted().toList());
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> settle(Map<String,Object> forecast, List<LotteryNumber> rows, Instant now, Map<String,Object> previous) {
        var date = LocalDate.parse((String) forecast.get("targetDate"));
        var actual = DrawQuality.history(rows).stream().filter(r -> forecast.get("category").equals(r.getCategory()) && date.equals(r.getDrawDate())).findFirst().orElse(null);
        boolean ready = actual != null && !now.isBefore(date.atTime(19,0).atZone(ZONE).toInstant());
        String status = ready ? "SETTLED" : previous != null && Set.of("SETTLED","RESULT_UNAVAILABLE").contains(previous.get("status")) ? "RESULT_UNAVAILABLE" : "PENDING";
        return map("forecastId", forecast.get("id"), "checkedAt", now.toString(), "resultFingerprint", ready ? DrawQuality.fingerprint(actual) : null,
                "status", status, "actualNumbers", ready ? actual.getNumbers().stream().sorted().toList() : null,
                "matched", ready ? hits((List<Integer>) forecast.get("numbers"), actual) : null,
                "frequencyMatched", ready ? hits((List<Integer>) forecast.get("frequencyNumbers"), actual) : null,
                "randomMatched", ready ? hits((List<Integer>) forecast.get("randomNumbers"), actual) : null);
    }
    private static int hits(List<Integer> ticket, LotteryNumber actual) { return (int) ticket.stream().filter(actual.getNumbers()::contains).count(); }

    @SuppressWarnings("unchecked")
    public static boolean isProspective(Map<String,Object> forecast) {
        try {
            Instant created = Instant.parse((String)forecast.get("createdAt"));
            LocalDate target = LocalDate.parse((String)forecast.get("targetDate"));
            List<Map<String,Object>> history = (List<Map<String,Object>>)forecast.get("history");
            var fingerprints = history.stream().map(r -> r.get("category") + "|" + r.get("drawDate") + "|"
                    + ((List<Number>)r.get("numbers")).stream().map(Number::intValue).sorted().map(String::valueOf).collect(Collectors.joining(","))
                    + "|" + Objects.toString(r.get("specialNumber"),"")).toList();
            return VERSION.equals(forecast.get("protocol")) && created.isBefore(target.atTime(18,0).atZone(ZONE).toInstant())
                    && LocalDate.parse((String)forecast.get("trainingThrough")).isBefore(target) && history.size() >= 5
                    && history.stream().allMatch(r -> forecast.get("category").equals(r.get("category")) && LocalDate.parse((String)r.get("drawDate")).isBefore(target))
                    && hash(fingerprints).equals(forecast.get("historyHash")) && hash(forecast.get("configuration")).equals(forecast.get("configurationHash"));
        } catch (Exception e) { return false; }
    }

    public static Map<String,Object> evaluate(List<Map<String,Object>> forecasts, List<Map<String,Object>> settlements, String category) {
        if (category != null && !Set.of("MEGA","POWER").contains(category)) throw new IllegalArgumentException("Invalid category");
        Map<Object,Map<String,Object>> latest = new HashMap<>();
        settlements.forEach(s -> latest.put(s.get("forecastId"), s));
        Map<String,List<Map<String,Object>>> groups = new LinkedHashMap<>();
        int excluded = 0;
        for (var forecast : forecasts) if (category == null || category.equals(forecast.get("category"))) {
            if (!isProspective(forecast)) { excluded++; continue; }
            String key = Stream.of("category","algorithm","modelVersion","codeVersion","configurationHash").map(k -> Objects.toString(forecast.get(k))).collect(Collectors.joining("|"));
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(forecast);
        }
        List<Map<String,Object>> reports = new ArrayList<>();
        for (var group : groups.entrySet()) {
            var entries = group.getValue(); var first = entries.get(0);
            var measured = entries.stream().map(f -> latest.get(f.get("id"))).filter(Objects::nonNull).filter(s -> "SETTLED".equals(s.get("status"))).toList();
            int n = measured.size(); int[] histogram = new int[7];
            measured.forEach(s -> histogram[((Number)s.get("matched")).intValue()]++);
            double delta = measured.stream().mapToDouble(s -> ((Number)s.get("matched")).doubleValue() - ((Number)s.get("frequencyMatched")).doubleValue()).average().orElse(0);
            double variance = measured.stream().mapToDouble(s -> Math.pow(((Number)s.get("matched")).doubleValue() - ((Number)s.get("frequencyMatched")).doubleValue() - delta, 2)).sum();
            double margin = n > 1 ? 1.96 * Math.sqrt(variance / (n-1) / n) : 0;
            reports.add(map("cohort", group.getKey(), "category", first.get("category"), "algorithm", first.get("algorithm"), "modelVersion", first.get("modelVersion"),
                    "configurationHash", first.get("configurationHash"), "savedForecasts", entries.size(), "evaluatedDraws", n, "pendingOrUnavailable", entries.size()-n,
                    "firstTargetDate", entries.stream().map(f -> (String)f.get("targetDate")).min(String::compareTo).orElse(null),
                    "lastTargetDate", entries.stream().map(f -> (String)f.get("targetDate")).max(String::compareTo).orElse(null),
                    "matchHistogram", histogram, "averageMatched", average(measured,"matched"), "frequencyAverageMatched", average(measured,"frequencyMatched"),
                    "randomAverageMatched", average(measured,"randomMatched"), "randomExpectedAverageMatched", 36.0 / ("POWER".equals(first.get("category")) ? 55 : 45),
                    "hit3RatePercent", n == 0 ? null : 100.0 * IntStream.rangeClosed(3,6).map(i -> histogram[i]).sum() / n,
                    "pairedDifferenceVsFrequency", n == 0 ? null : delta, "approximate95PercentInterval", n < 2 ? null : List.of(delta-margin,delta+margin),
                    "evidenceStatus", n < 30 ? "INSUFFICIENT_SAMPLE" : "DESCRIPTIVE_ONLY"));
        }
        return map("protocol", VERSION, "method", "prospective_frozen_forecasts", "excludedInvalidSnapshots", excluded, "note",
                "Chỉ đo vé đầu tiên lưu trước kỳ. Không chọn cấu hình từ tập đánh giá. Khoảng tin cậy xấp xỉ và chênh lệch chưa chứng minh cải thiện.", "groups", reports);
    }
    private static Double average(List<Map<String,Object>> rows, String key) { return rows.isEmpty() ? null : rows.stream().mapToDouble(r -> ((Number)r.get(key)).doubleValue()).average().orElseThrow(); }
}
