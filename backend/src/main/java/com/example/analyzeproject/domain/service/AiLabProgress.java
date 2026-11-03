package com.example.analyzeproject.domain.service;

import java.util.*;

/** Descriptive evidence from complete settled batches, never a prize or a promotion rule. */
public final class AiLabProgress {
    private AiLabProgress() {}

    public record Ticket(int matched, int frequencyMatched, int randomMatched) {
        public Ticket {
            if (matched < 0 || matched > 6 || frequencyMatched < 0 || frequencyMatched > 6
                    || randomMatched < 0 || randomMatched > 6)
                throw new IllegalArgumentException("Match counts must be between 0 and 6");
        }
    }

    public record Draw(String targetDate, List<Ticket> tickets) {
        public Draw { tickets = List.copyOf(tickets); }
    }

    public record Level(int threshold, int ticketHits, Double ticketPercent, int drawHits,
                        Double drawPercent, Double frequencyTicketPercent, Double randomTicketPercent) {}

    public record Point(String targetDate, int matched, int cumulativeMatched, double averageMatchedPerTicket) {}

    public record Report(String evaluationVersion, int evaluatedTickets, int totalMatched, int bestTicketMatched,
                         Double averageMatchedPerTicket, Double frequencyAverageMatched, Double randomAverageMatched,
                         Double recent30AverageMatched, Double previous30AverageMatched,
                         List<Integer> matchHistogram, List<Level> levels, List<Point> timeline) {}

    public static Report summarize(List<Draw> input) {
        var draws = input.stream().sorted(Comparator.comparing(Draw::targetDate)).toList();
        int tickets = 0, total = 0, frequency = 0, random = 0, best = 0;
        int[] histogram = new int[7], ticketHits = new int[3], frequencyHits = new int[3], randomHits = new int[3], drawHits = new int[3];
        List<Point> timeline = new ArrayList<>();
        for (var draw : draws) {
            if (draw.tickets().size() != AiLabModel.BUDGET)
                throw new IllegalArgumentException("Only complete equal-budget draws can be measured");
            int drawTotal = 0, drawBest = 0;
            for (var ticket : draw.tickets()) {
                tickets++;
                total += ticket.matched();
                drawTotal += ticket.matched();
                frequency += ticket.frequencyMatched();
                random += ticket.randomMatched();
                best = Math.max(best, ticket.matched());
                drawBest = Math.max(drawBest, ticket.matched());
                histogram[ticket.matched()]++;
                for (int level = 1; level <= 3; level++) {
                    if (ticket.matched() >= level) ticketHits[level - 1]++;
                    if (ticket.frequencyMatched() >= level) frequencyHits[level - 1]++;
                    if (ticket.randomMatched() >= level) randomHits[level - 1]++;
                }
            }
            for (int level = 1; level <= 3; level++) if (drawBest >= level) drawHits[level - 1]++;
            timeline.add(new Point(draw.targetDate(), drawTotal, total, (double) total / tickets));
        }
        List<Level> levels = new ArrayList<>();
        for (int level = 1; level <= 3; level++) levels.add(new Level(level, ticketHits[level - 1],
                percent(ticketHits[level - 1], tickets), drawHits[level - 1], percent(drawHits[level - 1], draws.size()),
                percent(frequencyHits[level - 1], tickets), percent(randomHits[level - 1], tickets)));
        int n = draws.size();
        return new Report("lab-progress-v1", tickets, total, best, average(total, tickets), average(frequency, tickets),
                average(random, tickets), windowAverage(draws.subList(Math.max(0, n - 30), n)),
                windowAverage(draws.subList(Math.max(0, n - 60), Math.max(0, n - 30))),
                Arrays.stream(histogram).boxed().toList(), List.copyOf(levels), List.copyOf(timeline));
    }

    private static Double percent(int count, int total) { return total == 0 ? null : 100.0 * count / total; }
    private static Double average(int count, int total) { return total == 0 ? null : (double) count / total; }
    private static Double windowAverage(List<Draw> draws) {
        return draws.isEmpty() ? null : draws.stream().flatMap(d -> d.tickets().stream()).mapToInt(Ticket::matched).average().orElseThrow();
    }
}
