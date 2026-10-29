package com.example.analyzeproject.service;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class PredictionParametersTest {
    @Test void acceptsOnlyConsumedParametersAndPreservesUntouchedConfiguration() throws Exception {
        var json=new ObjectMapper();
        var merged=json.readTree(PredictionParameters.merge("{\"version\":\"old\",\"weights\":{\"legacy\":3},\"recommendedAdjustments\":{\"temporalPriorStrength\":8}}","{\"temporalLearningRate\":0.4}"));
        assertEquals("old",merged.path("version").asText());assertEquals(3,merged.path("weights").path("legacy").asInt());
        assertEquals(8,merged.path("recommendedAdjustments").path("temporalPriorStrength").asInt());
        assertEquals(0.4,merged.path("recommendedAdjustments").path("temporalLearningRate").asDouble());
        for(String patch:new String[]{"{}","{\"evaluationSummary\":{}}","{\"learning_rate\":0.1}","{\"temporalMinHistory\":1.5}","{\"temporalPriorStrength\":0}","{\"temporalLearningRate\":\"0.4\"}","{\"poissonGapMinRatio\":3,\"poissonGapMaxRatio\":2}"})
            assertThrows(IllegalArgumentException.class,()->PredictionParameters.merge("{}",patch));
    }
}
