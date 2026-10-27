package com.example.analyzeproject.service;

import java.util.*;
import java.util.stream.IntStream;

/** Diversifies a fixed ticket budget. Rank utility is not a winning probability. */
final class TicketPortfolio {
    static final String VERSION = "rank-portfolio-v1";
    private TicketPortfolio() {}

    static List<List<Integer>> select(List<Integer> ranking, int max, int budget) {
        if ((max != 45 && max != 55) || budget < 1 || budget > 25)
            throw new IllegalArgumentException("Expected MEGA/POWER and 1..25 tickets");
        List<Integer> ordered = new ArrayList<>(new LinkedHashSet<>(ranking.stream()
                .filter(n -> n != null && n >= 1 && n <= max).toList()));
        IntStream.rangeClosed(1, max).filter(n -> !ordered.contains(n)).forEach(ordered::add);
        double[] utility = new double[max + 1];
        for (int i = 0; i < max; i++) utility[ordered.get(i)] = 1.0 - i / (double) max;
        Random random = new Random(20261028L + 31L * max + ordered.hashCode());
        LinkedHashSet<List<Integer>> pool = new LinkedHashSet<>();
        List<Integer> first = ordered.subList(0, 6).stream().sorted().toList();
        pool.add(first);
        // All numbers remain eligible; no parity, sum, zone or previous-draw exclusions.
        for (int attempt = 0; attempt < 1600; attempt++) {
            double[] keys = new double[max + 1];
            for (int n = 1; n <= max; n++)
                keys[n] = Math.log(Math.max(1e-12, random.nextDouble())) / (0.5 + utility[n]);
            pool.add(IntStream.rangeClosed(1, max).boxed()
                    .sorted(Comparator.<Integer>comparingDouble(n -> keys[n]).reversed().thenComparingInt(n -> n))
                    .limit(6).sorted().toList());
        }
        List<List<Integer>> selected = new ArrayList<>();
        selected.add(first); pool.remove(first);
        int[] exposure = new int[max + 1];
        first.forEach(n -> exposure[n]++);
        while (selected.size() < budget) {
            List<Integer> best = null; double bestScore = -Double.MAX_VALUE;
            for (List<Integer> candidate : pool) {
                if (selected.stream().anyMatch(t -> overlap(t, candidate) > 3)) continue;
                double score = 0;
                for (int n : candidate) score += utility[n] - 0.8 * exposure[n];
                if (score > bestScore) { best = candidate; bestScore = score; }
            }
            if (best == null) throw new IllegalStateException("Insufficient diverse ticket candidates");
            selected.add(best); pool.remove(best);
            best.forEach(n -> exposure[n]++);
        }
        return selected;
    }

    static int overlap(List<Integer> a, List<Integer> b) {
        return (int) a.stream().filter(b::contains).count();
    }

    static Map<String, Object> diagnostics(List<List<Integer>> tickets) {
        int largestOverlap = 0;
        Map<Integer, Integer> exposure = new TreeMap<>();
        for (int i = 0; i < tickets.size(); i++) {
            for (int n : tickets.get(i)) exposure.merge(n, 1, Integer::sum);
            for (int j = 0; j < i; j++) largestOverlap = Math.max(largestOverlap, overlap(tickets.get(i), tickets.get(j)));
        }
        return Map.of("version", VERSION, "ticketCount", tickets.size(), "uniqueNumbers", exposure.size(),
                "maxPairOverlap", largestOverlap, "numberExposure", exposure,
                "note", "Broader coverage at a fixed ticket budget; no demonstrated predictive advantage.");
    }
}
