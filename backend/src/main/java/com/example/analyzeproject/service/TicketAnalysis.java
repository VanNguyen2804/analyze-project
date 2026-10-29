package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.DrawReport.*;
import java.util.*;
import static com.example.analyzeproject.service.ForecastProtocol.map;

/** No persistence and no predicted seventh ball: a POWER ticket has exactly six numbers. */
public final class TicketAnalysis {
    private TicketAnalysis() {}
    public static TicketResult evaluate(String category, List<Integer> ticket, List<Integer> actual, Integer special,
                                        String id, String source, String sourceId, String label,
                                        List<Integer> original, boolean edited) {
        var numbers = WorkspaceProtocol.numbers(category, ticket);
        var matched = numbers.stream().filter(actual::contains).toList();
        var missed = numbers.stream().filter(n -> !actual.contains(n)).toList();
        boolean extra = "POWER".equals(category) && special != null && numbers.contains(special);
        return new TicketResult(id, source, sourceId, label, numbers, original, edited, matched, missed,
                matched.size(), extra, prize(category, matched.size(), extra));
    }
    public static String prize(String category, int matches, boolean special) {
        if (matches == 6) return "POWER".equals(category) ? "JACKPOT 1" : "JACKPOT";
        if (matches == 5 && "POWER".equals(category) && special) return "JACKPOT 2";
        return switch (matches) { case 5 -> "GIẢI NHẤT"; case 4 -> "GIẢI NHÌ"; case 3 -> "GIẢI BA"; default -> "KHÔNG TRÚNG"; };
    }
    public static Portfolio portfolio(List<TicketResult> entered) {
        Map<List<Integer>, TicketResult> unique = new LinkedHashMap<>();
        entered.forEach(t -> unique.putIfAbsent(t.numbers(), t));
        var tickets = new ArrayList<>(unique.values());
        var coverage = tickets.stream().flatMap(t -> t.matchedNumbers().stream()).distinct().sorted().toList();
        Map<Integer,Integer> usage = new TreeMap<>();
        tickets.forEach(t -> t.numbers().forEach(n -> usage.merge(n, 1, Integer::sum)));
        List<Integer> histogram = new ArrayList<>(Collections.nCopies(7, 0));
        tickets.forEach(t -> histogram.set(t.matchedCount(), histogram.get(t.matchedCount()) + 1));
        List<List<Integer>> overlaps = tickets.stream().map(a -> tickets.stream()
                .map(b -> (int)a.numbers().stream().filter(b.numbers()::contains).count()).toList()).toList();
        return new Portfolio(entered.size(), tickets.size(), entered.size()-tickets.size(), usage.size(),
                new ArrayList<>(usage.keySet()), tickets.stream().mapToInt(TicketResult::matchedCount).sum(), coverage,
                coverage.size(), Math.round(coverage.size()*1000.0/6)/10.0,
                tickets.isEmpty()?null:tickets.stream().mapToInt(TicketResult::matchedCount).max().orElseThrow(),
                tickets.isEmpty()?null:tickets.stream().mapToInt(TicketResult::matchedCount).average().orElseThrow(),
                histogram, overlaps, usage);
    }
    /** Adapter for the older reconciliation API; the arithmetic is shared with the new report. */
    public static Map<String,Object> legacy(List<Integer> ticket, List<Integer> actual, Integer special, String category) {
        var r=evaluate(category,ticket,actual,special,"","","","",null,false);
        return map("matchedNumbers",r.matchedNumbers(),"matchedCount",r.matchedCount(),"matchedSpecial",r.matchedSpecial(),"prize",r.prize());
    }
}
