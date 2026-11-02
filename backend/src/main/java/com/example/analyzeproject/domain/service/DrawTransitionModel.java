package com.example.analyzeproject.domain.service;

import com.example.analyzeproject.domain.model.LotteryNumber;
import java.util.*;

/** A blend of one-step transition distributions from the last two observed draw contexts. */
public final class DrawTransitionModel {
    public static final String VERSION = "scheduled-transitions-v2";
    private DrawTransitionModel() {}

    public record Result(double[] scores, int learnedTransitions, int skippedGapTransitions) {}

    public static Result fit(List<LotteryNumber> history, String category) {
        int max = "POWER".equals(category) ? 55 : 45;
        var rows = DrawQuality.history(history).stream().filter(r -> category.equals(r.getCategory()))
                .sorted(Comparator.comparing(LotteryNumber::getDrawDate)).toList();
        int[][] counts = new int[max + 1][max + 1]; int[] exposures = new int[max + 1];
        int learned = 0, skipped = 0;
        for (int t = 1; t < rows.size(); t++) {
            var previous = rows.get(t - 1); var target = rows.get(t);
            if (!TemporalRelationshipModel.scheduledAfter(previous.getDrawDate(),category).equals(target.getDrawDate())) {
                skipped++; continue;
            }
            learned++;
            for (int source : previous.getNumbers()) {
                exposures[source]++;
                for (int number : target.getNumbers()) counts[source][number]++;
            }
        }
        double[] scores = new double[max + 1];
        if (!rows.isEmpty()) {
            var last = rows.get(rows.size() - 1);
            var older = rows.size() > 1 ? rows.get(rows.size() - 2) : null;
            boolean olderIsAdjacent = older != null && TemporalRelationshipModel.scheduledAfter(older.getDrawDate(),category).equals(last.getDrawDate());
            for (int number = 1; number <= max; number++) {
                for (int source : last.getNumbers()) scores[number] += 0.70 * counts[source][number] / Math.max(1.0,exposures[source]);
                if (olderIsAdjacent) for (int source : older.getNumbers()) scores[number] += 0.30 * counts[source][number] / Math.max(1.0,exposures[source]);
            }
        }
        return new Result(scores, learned, skipped);
    }
}
