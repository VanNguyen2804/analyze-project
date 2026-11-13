package com.example.analyzeproject.domain.service;

import java.util.*;
import java.util.stream.IntStream;

/** Bounded, deterministic coverage experiment. Its objective is not a probability of winning. */
public final class CoveragePortfolio {
    public static final String VERSION = "quad-coverage-v1";
    public record Policy(int candidates, double rankWeight, double exposurePenalty, boolean anchorTopSix) {
        public Policy {
            if (candidates < 100 || candidates > 2000 || !Double.isFinite(rankWeight) || rankWeight < 0 || rankWeight > 5
                    || !Double.isFinite(exposurePenalty) || exposurePenalty < 0 || exposurePenalty > 5)
                throw new IllegalArgumentException("Invalid coverage policy");
        }
        public static Policy defaults() { return new Policy(320, .35, .10, true); }
    }
    private CoveragePortfolio() {}

    public static List<List<Integer>> select(List<Integer> ranking, int max, int budget, String seed, Policy policy) {
        return select(ranking,max,budget,seed,policy,false);
    }
    public static List<List<Integer>> selectTriples(List<Integer> ranking,int max,int budget,String seed,Policy policy) {
        return select(ranking,max,budget,seed,policy,true);
    }
    private static List<List<Integer>> select(List<Integer> ranking,int max,int budget,String seed,Policy policy,boolean triplesFirst) {
        if ((max != 45 && max != 55) || budget < 1 || budget > 25) throw new IllegalArgumentException("Invalid domain or budget");
        var ordered = new ArrayList<>(new LinkedHashSet<>(ranking.stream().filter(n -> n != null && n >= 1 && n <= max).toList()));
        IntStream.rangeClosed(1, max).filter(n -> !ordered.contains(n)).forEach(ordered::add);
        double[] utility = new double[max + 1];
        for (int i = 0; i < max; i++) utility[ordered.get(i)] = 1 - i / (double) max;
        var first = ordered.subList(0, 6).stream().sorted().toList();
        var pool = new LinkedHashSet<List<Integer>>(); pool.add(first);
        var random = new Random(Objects.hash(VERSION, seed, ordered));
        for (int attempt = 0; attempt < policy.candidates(); attempt++) {
            double[] keys = new double[max + 1];
            for (int n = 1; n <= max; n++) keys[n] = Math.log(Math.max(1e-12, random.nextDouble())) / (.25 + utility[n]);
            pool.add(IntStream.rangeClosed(1, max).boxed().sorted(Comparator.<Integer>comparingDouble(n -> keys[n]).reversed()
                    .thenComparingInt(n -> n)).limit(6).sorted().toList());
        }
        var result = new ArrayList<List<Integer>>();
        if (policy.anchorTopSix()) result.add(first);
        while (result.size() < budget) {
            List<Integer> best = null; double bestScore = Double.NEGATIVE_INFINITY;
            for (var candidate : pool) {
                if (result.contains(candidate)) continue;
                var trial = new ArrayList<>(result); trial.add(candidate);
                double score = objective(trial, utility, policy, triplesFirst);
                if (score > bestScore) { best = candidate; bestScore = score; }
            }
            if (best == null) throw new IllegalStateException("Insufficient candidates");
            result.add(best);
        }
        // Two bounded local-search passes; keep the anchor when the declared policy requests it.
        for (int pass = 0; pass < 2; pass++) for (int slot = policy.anchorTopSix() ? 1 : 0; slot < budget; slot++) {
            double bestScore = objective(result, utility, policy, triplesFirst); var best = result.get(slot);
            for (var candidate : pool) {
                if (result.contains(candidate)) continue;
                var trial = new ArrayList<>(result); trial.set(slot, candidate);
                double score = objective(trial, utility, policy, triplesFirst);
                if (score > bestScore + 1e-12) { bestScore = score; best = candidate; }
            }
            result.set(slot, best);
        }
        return List.copyOf(result);
    }

    private static double objective(List<List<Integer>> tickets, double[] utility, Policy policy, boolean triplesFirst) {
        Set<Long> triples = new HashSet<>(), quads = new HashSet<>();
        int[] exposure = new int[utility.length]; double rank = 0, repeated = 0;
        for (var ticket : tickets) {
            for (int n : ticket) { rank += utility[n]; repeated += exposure[n]++; }
            subsets(ticket, triples, quads);
        }
        return (triplesFirst ? triples.size()/20.0 + .2*quads.size()/15.0 : quads.size()/15.0 + .2*triples.size()/20.0) + policy.rankWeight() * rank / 6.0 - policy.exposurePenalty() * repeated;
    }
    private static void subsets(List<Integer> ticket, Set<Long> triples, Set<Long> quads) {
        for (int a = 0; a < 6; a++) for (int b = a + 1; b < 6; b++) for (int c = b + 1; c < 6; c++) {
            triples.add((1L << ticket.get(a)) | (1L << ticket.get(b)) | (1L << ticket.get(c)));
            for (int d = c + 1; d < 6; d++) quads.add((1L << ticket.get(a)) | (1L << ticket.get(b)) | (1L << ticket.get(c)) | (1L << ticket.get(d)));
        }
    }
    public static Map<String,Object> diagnostics(List<List<Integer>> tickets) {
        Set<Long> triples = new HashSet<>(), quads = new HashSet<>();
        tickets.forEach(t -> subsets(t, triples, quads));
        var result = new LinkedHashMap<>(TicketPortfolio.diagnostics(tickets));
        result.put("version", VERSION); result.put("coveredTriples", triples.size()); result.put("coveredQuadruples", quads.size());
        result.put("scoreType", "coverage_objective"); return result;
    }
}
