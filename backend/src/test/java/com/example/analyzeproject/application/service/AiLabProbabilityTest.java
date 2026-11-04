package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.service.AiLabModel;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiLabProbabilityTest {
    private Map<String,Object> forecast(double probability) {
        return Map.of("category","MEGA","labModel","logistic","explanation",Map.of("details",
                IntStream.rangeClosed(1,45).mapToObj(n -> Map.of("number",n,"score",probability)).toList()));
    }
    @Test void uniformMarginalsMatchAnalyticBaselineAndCountOneDraw() {
        var metrics = new AiLabProbability();
        metrics.add(forecast(6.0/45),Map.of("actualNumbers",List.of(1,2,3,4,5,6)));
        var report = metrics.report();
        assertEquals(1,report.get("evaluatedDraws"));
        assertEquals(45,report.get("observations"));
        assertEquals((6.0/45)*(1-6.0/45),(Double)report.get("brierScore"),1e-12);
        assertEquals(report.get("brierScore"),report.get("uniformBrierScore"));
        assertEquals(report.get("logLoss"),report.get("uniformLogLoss"));
    }
    @Test void missingInvalidAndHeuristicScoresAreNotInvented() {
        var metrics = new AiLabProbability();
        metrics.add(Map.of("labModel","logistic"),Map.of());
        metrics.add(forecast(Double.NaN),Map.of("actualNumbers",List.of(1,2,3,4,5,6)));
        var heuristic = new HashMap<>(forecast(.2)); heuristic.put("labModel","recency");
        metrics.add(heuristic,Map.of("actualNumbers",List.of(1,2,3,4,5,6)));
        assertEquals(0,metrics.report().get("evaluatedDraws"));
        assertNull(metrics.report().get("brierScore"));
    }
    @Test void extremePredictionsHaveFiniteLossAndNoFeedbackFitsSameBaseModel() {
        var metrics = new AiLabProbability();
        metrics.add(forecast(0),Map.of("actualNumbers",List.of(1,2,3,4,5,6)));
        assertTrue(Double.isFinite((Double)metrics.report().get("logLoss")));
        assertArrayEquals(AiLabModel.scores(List.of(),"MEGA","logistic"),
                AiLabModel.scores(List.of(),"MEGA","logistic_no_feedback"));
    }
}
