package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.application.port.out.*;
import com.example.analyzeproject.application.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PredictionParametersHttpTest {
    @Test void obsoleteParameterPatchesReturn400WithoutSavingAConfiguration() throws Exception {
        var parameters=mock(AlgorithmHyperparameterRepository.class);
        var service=new AnalyzeService(mock(LotteryNumberRepository.class),mock(UserTicketRepository.class),parameters,null);
        var http=MockMvcBuilders.standaloneSetup(new AnalyzeController(service,mock(ForecastService.class),mock(DrawAnalysisService.class))).build();
        for(String key:new String[]{"poissonGapMinRatio","poissonGapMaxRatio","repeatExhaustionPenalty"}) {
            String body=new ObjectMapper().writeValueAsString(Map.of("category","MEGA","hyperparametersJson","{\""+key+"\":1}"));
            http.perform(post("/api/analyze/update-algorithm").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(key)));
        }
        verify(parameters,never()).save(any()); verify(parameters,never()).count();
    }
}
