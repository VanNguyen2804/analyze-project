package com.example.analyzeproject.application.service;
import com.example.analyzeproject.domain.service.*;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

    @Test void retiredKeysAreRejectedButOldStoredValuesArePreserved() throws Exception {
        var json=new ObjectMapper();
        String original="{\"recommendedAdjustments\":{\"poissonGapMinRatio\":9,\"poissonGapMaxRatio\":1,\"repeatExhaustionPenalty\":-0.45}}";
        var merged=json.readTree(PredictionParameters.merge(original,"{\"coOccurrenceWeight\":0.5}"));
        assertEquals(9,merged.path("recommendedAdjustments").path("poissonGapMinRatio").asInt());
        assertEquals(0.5,merged.path("recommendedAdjustments").path("coOccurrenceWeight").asDouble());
        for(String key:new String[]{"poissonGapMinRatio","poissonGapMaxRatio","repeatExhaustionPenalty"}) {
            var error=assertThrows(IllegalArgumentException.class,()->PredictionParameters.merge(original,"{\""+key+"\":1,\"coOccurrenceWeight\":0.1}"));
            assertTrue(error.getMessage().contains(key));
        }
    }

    @Test void pairParametersAreFiniteBoundedAndSupportIsIntegral() throws Exception {
        var value=new ObjectMapper().readTree(PredictionParameters.merge("{}","{\"pairGraphPriorStrength\":8,\"pairGraphMinSupport\":3}"));
        assertEquals(8,value.path("pairGraphPriorStrength").asInt()); assertEquals(3,value.path("pairGraphMinSupport").asInt());
        for(String patch:List.of("{\"pairGraphMinSupport\":1.5}","{\"pairGraphMinSupport\":0}","{\"pairGraphMinSupport\":1001}",
                "{\"pairGraphPriorStrength\":0}","{\"pairGraphPriorStrength\":1e309}","{\"pairGraphPriorStrength\":\"8\"}"))
            assertThrows(IllegalArgumentException.class,()->PredictionParameters.merge("{}",patch));
    }
}
