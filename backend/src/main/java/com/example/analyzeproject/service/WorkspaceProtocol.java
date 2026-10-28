package com.example.analyzeproject.service;

import com.example.analyzeproject.model.LotteryNumber;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import static com.example.analyzeproject.service.ForecastProtocol.*;

/** Pure rules shared by the interactive workspace and its offline evaluation. */
public final class WorkspaceProtocol {
    public static final String VERSION = "user-workspace-v1";
    private WorkspaceProtocol() {}
    public static String category(String value) {
        if (!Set.of("MEGA", "POWER").contains(Objects.toString(value, ""))) throw new IllegalArgumentException("Loại xổ số không hợp lệ.");
        return value;
    }
    public static List<Integer> numbers(String category, List<Integer> values) {
        category(category);
        if (values == null || values.size() != 6 || new HashSet<>(values).size() != 6) throw new IllegalArgumentException("Vé cần sáu số khác nhau.");
        int max = "MEGA".equals(category) ? 45 : 55;
        if (values.stream().anyMatch(n -> n == null || n < 1 || n > max)) throw new IllegalArgumentException("Số trên vé nằm ngoài miền cho phép.");
        return values.stream().sorted().toList();
    }
    public static LocalDate previous(String category, LocalDate target) {
        return ForecastProtocol.previous(category,target);
    }
    public static boolean ready(List<LotteryNumber> history, String category, LocalDate target) {
        return history.size() >= 10 && history.get(history.size()-1).getDrawDate().equals(previous(category, target));
    }
    public static String historyHash(List<LotteryNumber> history) { return hash(history.stream().map(DrawQuality::fingerprint).toList()); }
    public static List<Integer> combine(String category, List<Integer> first, List<Integer> second, double weight, List<Integer> keep, List<Integer> exclude) {
        int max = "MEGA".equals(category(category)) ? 45 : 55;
        if (!Double.isFinite(weight) || weight < 0 || weight > 1) throw new IllegalArgumentException("Trọng số phải từ 0 đến 1.");
        if (first.size()!=max || second.size()!=max || new HashSet<>(first).size()!=max || new HashSet<>(second).size()!=max
                || Stream.concat(first.stream(),second.stream()).anyMatch(n -> n==null || n<1 || n>max)) throw new IllegalArgumentException("Thiếu bảng xếp hạng hợp lệ.");
        keep = keep == null ? List.of() : keep; exclude = exclude == null ? List.of() : exclude;
        if (keep.size()>6 || new HashSet<>(keep).size()!=keep.size() || new HashSet<>(exclude).size()!=exclude.size()
                || Stream.concat(keep.stream(),exclude.stream()).anyMatch(n -> n==null || n<1 || n>max)
                || keep.stream().anyMatch(exclude::contains) || max-exclude.size()<6) throw new IllegalArgumentException("Danh sách giữ/bỏ số không hợp lệ hoặc mâu thuẫn.");
        Map<Integer,Double> score = new HashMap<>();
        for(int i=0;i<max;i++) { score.merge(first.get(i),weight/(i+1),Double::sum); score.merge(second.get(i),(1-weight)/(i+1),Double::sum); }
        List<Integer> result = new ArrayList<>(keep); Set<Integer> blocked = new HashSet<>(exclude);
        score.keySet().stream().filter(n -> !blocked.contains(n) && !result.contains(n))
                .sorted(Comparator.<Integer>comparingDouble(score::get).reversed().thenComparingInt(n->n))
                .limit(6-result.size()).forEach(result::add);
        return numbers(category,result);
    }
    /** Equal ticket budgets, seeded only by protocol/category/date/group, never by outcomes. */
    public static List<List<Integer>> controls(List<LotteryNumber> history, String category, int count, String seed, boolean frequency) {
        if(frequency && history.isEmpty()) return List.of();
        int max = "MEGA".equals(category) ? 45 : 55; int[] counts = new int[max+1];
        history.forEach(r -> r.getNumbers().forEach(n -> counts[n]++));
        Random rng = new Random((VERSION+":"+seed+":"+frequency).hashCode());
        Set<List<Integer>> unique = new LinkedHashSet<>();
        for(int attempt=0;unique.size()<count && attempt<10000;attempt++) {
            Map<Integer,Double> keys = new HashMap<>();
            for(int n=1;n<=max;n++) keys.put(n, frequency && attempt==0 ? (double)counts[n] : Math.log(Math.max(1e-12,rng.nextDouble()))/(frequency?counts[n]+1.0:1.0));
            unique.add(IntStream.rangeClosed(1,max).boxed().sorted(Comparator.<Integer>comparingDouble(keys::get).reversed().thenComparingInt(n->n)).limit(6).sorted().toList());
        }
        if(unique.size()!=count) throw new IllegalStateException("Không tạo đủ vé đối chứng.");
        return new ArrayList<>(unique);
    }
    public static Map<String,Object> metrics(List<List<Integer>> tickets, List<Integer> actual) {
        var hits = tickets.stream().map(t -> t.stream().filter(actual::contains).distinct().sorted().toList()).toList();
        var coverage = hits.stream().flatMap(Collection::stream).distinct().sorted().toList();
        return map("ticketCount",tickets.size(),"matches",hits.stream().map(List::size).toList(),"matchedNumbers",hits,
                "bestMatched",hits.stream().mapToInt(List::size).max().orElse(0),"coverage",coverage,"coverageCount",coverage.size(),
                "averageMatched",hits.stream().mapToInt(List::size).average().orElse(0),"hit3",hits.stream().anyMatch(t->t.size()>=3),"hit4",hits.stream().anyMatch(t->t.size()>=4));
    }
    public static String codeVersion() { return fingerprint(WorkspaceProtocol.class, WorkspaceService.class, PredictionSupport.class, AnalyzeService.class, TemporalRelationshipModel.class, ForecastProtocol.class, DrawQuality.class); }
    public static String fingerprint(Class<?>... types) {
        List<String> parts = new ArrayList<>();
        try {
            for(Class<?> type:types) try(var in=type.getResourceAsStream(type.getSimpleName()+".class")) { parts.add(HexFormat.of().formatHex(in.readAllBytes())); }
        } catch(Exception e) { throw new IllegalStateException(e); }
        return hash(parts);
    }
}
