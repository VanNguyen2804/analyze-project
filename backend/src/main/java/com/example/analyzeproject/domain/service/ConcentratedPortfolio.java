package com.example.analyzeproject.domain.service;

import java.util.*;

/** Experimental fixed-budget assembly; rank utility is not a win probability. */
public final class ConcentratedPortfolio {
    public static final String VERSION = "concentrated-hybrid-v1";
    public record Policy(int topK, int focusedTickets, double overlapPenalty) {
        public Policy {
            if (topK < 6 || topK > 16 || focusedTickets < 1 || focusedTickets > 5 || (topK == 6 && focusedTickets > 1)
                    || !Double.isFinite(overlapPenalty) || overlapPenalty < 0 || overlapPenalty > 2)
                throw new IllegalArgumentException("Invalid concentration policy");
        }
        public static Policy focused() { return new Policy(12, 5, .15); }
        public static Policy hybrid() { return new Policy(12, 3, .15); }
    }
    private ConcentratedPortfolio() {}
    public static List<List<Integer>> select(List<Integer> ranking, int max, int budget, String seed, Policy policy) {
        if ((max != 45 && max != 55) || budget < 1 || budget > 5 || ranking.size() != max
                || new HashSet<>(ranking).size() != max || ranking.stream().anyMatch(n -> n == null || n < 1 || n > max))
            throw new IllegalArgumentException("Complete ranking and budget 1..5 required");
        double[] utility = new double[max + 1];
        for (int i = 0; i < max; i++) utility[ranking.get(i)] = 1 - i / (double) max;
        List<List<Integer>> candidates = new ArrayList<>();
        combinations(ranking.subList(0, policy.topK()), 0, new ArrayList<>(), candidates);
        List<List<Integer>> selected = new ArrayList<>();
        selected.add(ranking.subList(0, 6).stream().sorted().toList());
        while (selected.size() < Math.min(budget, policy.focusedTickets())) {
            if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Portfolio selection interrupted");
            List<Integer> best = null; double score = Double.NEGATIVE_INFINITY;
            for (var ticket : candidates) {
                if (selected.contains(ticket)) continue;
                double value = ticket.stream().mapToDouble(n -> utility[n]).sum();
                double novelty = 0;
                for (int mask = 0; mask < 64; mask++) {
                    int size = Integer.bitCount(mask); if (size != 4 && size != 5) continue;
                    long subset=0; double weight=0;
                    for(int i=0;i<6;i++)if((mask & (1<<i))!=0){subset|=1L<<ticket.get(i);weight+=utility[ticket.get(i)];}
                    boolean seen=false;
                    for(var previous:selected){long bits=0;for(int n:previous)bits|=1L<<n;if((bits&subset)==subset){seen=true;break;}}
                    if(!seen)novelty+=weight/size/(size==4?15:6);
                }
                double overlap = 0;
                for (var previous : selected) {
                    long common = ticket.stream().filter(previous::contains).count(); overlap += common * common / 36.0;
                }
                value = value / 6 + novelty - policy.overlapPenalty() * overlap;
                if (value > score + 1e-12) { best = ticket; score = value; }
            }
            if (best == null) throw new IllegalStateException("Insufficient focused candidates");
            selected.add(best);
        }
        if (selected.size() < budget) {
            // Preserve whole-domain exploration without replacing the focused anchor.
            var broad = CoveragePortfolio.select(ranking, max, 5, seed, CoveragePortfolio.Policy.defaults());
            for (var ticket : broad) if (!selected.contains(ticket) && selected.size() < budget) selected.add(ticket);
        }
        if (selected.size() != budget) throw new IllegalStateException("Incomplete portfolio");
        return List.copyOf(selected);
    }
    private static void combinations(List<Integer> pool, int start, List<Integer> current, List<List<Integer>> result) {
        if (current.size() == 6) { result.add(current.stream().sorted().toList()); return; }
        for (int i = start; i <= pool.size() - (6 - current.size()); i++) {
            current.add(pool.get(i)); combinations(pool, i + 1, current, result); current.remove(current.size() - 1);
        }
    }
}
