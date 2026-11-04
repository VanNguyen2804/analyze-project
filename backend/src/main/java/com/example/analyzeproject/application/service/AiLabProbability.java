package com.example.analyzeproject.application.service;

import java.util.*;
import static com.example.analyzeproject.application.service.ForecastProtocol.map;

/** Scores frozen marginal predictions once per draw, never once per ticket. */
final class AiLabProbability {
    private int draws, observations;
    private double brier, loss, baselineBrier, baselineLoss;
    private final int[] counts = new int[10], positives = new int[10];
    private final double[] sums = new double[10];

    void add(Map<String,Object> forecast, Map<String,Object> settlement) {
        if (!Set.of("logistic", "logistic_no_feedback").contains(forecast.get("labModel"))) return;
        if (!(forecast.get("explanation") instanceof Map<?,?> explanation)
                || !(explanation.get("details") instanceof List<?> details)
                || !(settlement.get("actualNumbers") instanceof List<?> actual)) return;
        int max = "POWER".equals(forecast.get("category")) ? 55 : 45;
        if (details.size() != max || actual.size() != 6 || new HashSet<>(actual).size() != 6) return;
        double[] probabilities = new double[max + 1];
        boolean[] seen = new boolean[max + 1], outcomes = new boolean[max + 1];
        for (Object value : actual) {
            if (!(value instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue() < 1 || n.intValue() > max) return;
            outcomes[n.intValue()] = true;
        }
        for (Object item : details) {
            if (!(item instanceof Map<?,?> detail) || !(detail.get("number") instanceof Number n)
                    || !(detail.get("score") instanceof Number score)) return;
            int number = n.intValue(); double p = score.doubleValue();
            if (n.doubleValue() != number || number < 1 || number > max || seen[number]
                    || !Double.isFinite(p) || p < 0 || p > 1) return;
            seen[number] = true; probabilities[number] = p;
        }
        double base = 6.0 / max;
        for (int n = 1; n <= max; n++) {
            double p = probabilities[n], y = outcomes[n] ? 1 : 0;
            brier += (p-y)*(p-y); baselineBrier += (base-y)*(base-y);
            double clipped = Math.max(1e-15, Math.min(1-1e-15, p));
            loss -= y*Math.log(clipped)+(1-y)*Math.log1p(-clipped);
            baselineLoss -= y*Math.log(base)+(1-y)*Math.log1p(-base);
            int bin = Math.min(9, (int)(p*10));
            counts[bin]++; positives[bin] += (int)y; sums[bin] += p;
        }
        draws++; observations += max;
    }

    Map<String,Object> report() {
        List<Map<String,Object>> bins = new ArrayList<>();
        for (int i=0; i<10; i++) bins.add(map("lower",i/10.0,"upper",(i+1)/10.0,"count",counts[i],
                "predicted",counts[i]==0?null:sums[i]/counts[i],"observed",counts[i]==0?null:(double)positives[i]/counts[i]));
        return map("evaluatedDraws",draws,"observations",observations,
                "brierScore",observations==0?null:brier/observations,"logLoss",observations==0?null:loss/observations,
                "uniformBrierScore",observations==0?null:baselineBrier/observations,
                "uniformLogLoss",observations==0?null:baselineLoss/observations,"bins",bins);
    }
}
