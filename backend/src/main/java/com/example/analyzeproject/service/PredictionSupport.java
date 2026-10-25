package com.example.analyzeproject.service;

import java.util.*;
import java.util.stream.Collectors;
import java.time.*;
import com.example.analyzeproject.model.LotteryNumber;

/** Pure selection helpers. Rank scores are not calibrated winning probabilities. */
final class PredictionSupport {
    private PredictionSupport() {}

    static List<Integer> fuse(List<Integer> first, List<Integer> second, int count) {
        Map<Integer, Double> scores = new HashMap<>();
        for (List<Integer> ranking : List.of(first, second)) {
            List<Integer> unique = new ArrayList<>(new LinkedHashSet<>(ranking));
            for (int i = 0; i < unique.size(); i++) {
                scores.merge(unique.get(i), 1.0 / (i + 1), Double::sum);
            }
        }
        return scores.keySet().stream()
                .sorted(Comparator.<Integer>comparingDouble(scores::get).reversed().thenComparingInt(n -> n))
                .limit(count).sorted().collect(Collectors.toList());
    }

    static List<Integer> distinctTicket(List<Integer> preferred, List<Integer> fallback, int max) {
        return java.util.stream.Stream.concat(preferred.stream(), fallback.stream())
                .filter(n -> n != null && n >= 1 && n <= max).distinct().limit(6).sorted()
                .collect(Collectors.toList());
    }

    static Integer specialFor(Integer preferred, List<Integer> ticket, List<Integer> ranking) {
        if (preferred == null) return null;
        if (!ticket.contains(preferred)) return preferred;
        return ranking.stream().filter(n -> !ticket.contains(n)).findFirst().orElse(null);
    }

    static String composition(List<Integer> numbers) {
        long even = numbers.stream().filter(n -> n % 2 == 0).count();
        return String.format("%d Chẵn / %d Lẻ • Tổng = %d", even, numbers.size() - even,
                numbers.stream().mapToInt(Integer::intValue).sum());
    }

    static LocalDate nextDraw(boolean power, ZonedDateTime instant, LocalDate latest) {
        ZonedDateTime vietnam = instant.withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate today = vietnam.toLocalDate();
        Set<DayOfWeek> days = power ? Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
                : Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY);
        LocalDate candidate = latest != null && latest.isAfter(today) ? latest.plusDays(1) : today;
        while (!days.contains(candidate.getDayOfWeek()) || (latest != null && !candidate.isAfter(latest))
                || (candidate.equals(today) && !vietnam.toLocalTime().isBefore(LocalTime.of(18, 0)))) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    static Map<String, Object> commonPatterns(List<LotteryNumber> draws, int max) {
        int drift = 0;
        for (int i = 1; i < draws.size(); i++) {
            List<Integer> previous = draws.get(i - 1).getNumbers();
            if (draws.get(i).getNumbers().stream().filter(n -> !previous.contains(n) && previous.stream().anyMatch(p -> Math.abs(n - p) == 1)).count() >= 2) drift++;
        }
        int low = 3 * (max + 1) - max, high = 3 * (max + 1) + max;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evaluatedDraws", draws.size());
        result.put("amplitude1Pattern", pattern("Ít nhất hai số lân cận từ bản ghi trước", drift, Math.max(0, draws.size() - 1)));
        result.put("parityPattern", observed(draws, "Hai đến bốn số chẵn", n -> { long even = n.stream().filter(v -> v % 2 == 0).count(); return even >= 2 && even <= 4; }));
        result.put("zoneDistributionPattern", observed(draws, "Có số ở cả ba phân vùng", n -> n.stream().map(v -> Math.min(2, (v - 1) * 3 / max)).distinct().count() == 3));
        result.put("tailModuloPattern", observed(draws, "Ít nhất một cặp cùng đuôi", n -> n.stream().map(v -> v % 10).distinct().count() < n.size()));
        result.put("consecutivePairPattern", observed(draws, "Ít nhất một cặp liên tiếp", n -> n.stream().anyMatch(v -> n.contains(v + 1))));
        Map<String, Object> sums = observed(draws, "Tổng trong một miền số quanh trung bình đều", n -> { int sum = n.stream().mapToInt(Integer::intValue).sum(); return sum >= low && sum <= high; });
        sums.put("optimalRange", low + " - " + high); result.put("sumRangePattern", sums);
        return result;
    }

    private static Map<String, Object> observed(List<LotteryNumber> draws, String rule, java.util.function.Predicate<List<Integer>> predicate) {
        return pattern(rule, (int) draws.stream().filter(r -> predicate.test(r.getNumbers())).count(), draws.size());
    }

    private static Map<String, Object> pattern(String rule, int count, int total) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rule", rule); result.put("occurrencesCount", count);
        result.put("percentage", total == 0 ? 0.0 : Math.round(1000.0 * count / total) / 10.0);
        result.put("insight", count + "/" + total + " quan sát trong dữ liệu đã lưu.");
        return result;
    }
}
