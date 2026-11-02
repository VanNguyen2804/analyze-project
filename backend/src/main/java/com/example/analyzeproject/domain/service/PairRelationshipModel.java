package com.example.analyzeproject.domain.service;

import java.util.*;

/** Signed, smoothed pair excess relative to empirical marginals, conditioned on the last draw. */
public final class PairRelationshipModel {
    public static final String VERSION = "pair-excess-v1";
    public static final double DEFAULT_PRIOR_STRENGTH = 8;
    public static final int DEFAULT_MIN_SUPPORT = 3;
    private PairRelationshipModel() {}

    public record Result(double[] scores, int[] supportedPairs) {}

    public static Result score(int max, int draws, int[] frequency, int[][] pairs, List<Integer> context,
            double priorStrength, int minSupport) {
        if (max != 45 && max != 55) throw new IllegalArgumentException("Expected MEGA/POWER domain");
        double prior = Double.isFinite(priorStrength) && priorStrength >= 0.01 && priorStrength <= 10000
                ? priorStrength : DEFAULT_PRIOR_STRENGTH;
        int minimum = minSupport >= 1 && minSupport <= 1000 ? minSupport : DEFAULT_MIN_SUPPORT;
        double[] scores = new double[max + 1]; int[] supports = new int[max + 1];
        if (draws <= 0 || context == null || context.isEmpty()) return new Result(scores, supports);
        var seeds = context.stream().filter(Objects::nonNull).filter(n -> n >= 1 && n <= max).distinct().sorted().toList();
        // Under uniform sampling of six distinct balls, P(j|i)=5/(max-1), not 6/max.
        // This correction gives the exact uniform null. With empirical unequal marginals it is a heuristic null.
        double correction = max * 5.0 / (6.0 * (max - 1));
        for (int candidate = 1; candidate <= max; candidate++) {
            int denominator = 0;
            for (int seed : seeds) {
                if (seed == candidate) continue;
                denominator++;
                int support = Math.min(frequency[candidate], frequency[seed]);
                if (support < minimum) continue;
                double lower = Math.max(0, frequency[candidate] + frequency[seed] - draws);
                double expected = Math.max(lower, Math.min(support, correction * frequency[candidate] * (double) frequency[seed] / draws));
                // Shrinks residuals toward zero; a pair with no observations may carry negative evidence.
                scores[candidate] += (pairs[candidate][seed] - expected) / (support + prior);
                supports[candidate]++;
            }
            // Unsupported pairs contribute zero, rather than inflating the remaining sparse evidence.
            if (denominator > 0) scores[candidate] /= denominator;
        }
        return new Result(scores, supports);
    }
}
