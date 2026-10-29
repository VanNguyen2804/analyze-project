package com.example.analyzeproject.service;

import com.example.analyzeproject.controller.DrawAnalysisController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DrawAnalysisController.class)
class DrawAnalysisHttpTest {
    @MockBean DrawAnalysisService service;
    @Autowired MockMvc mvc;
    @Test void decimalNumbersAreRejectedBeforeTheReadOnlyEvaluatorRuns() throws Exception {
        mvc.perform(post("/api/analyze/evaluate-tickets").contentType("application/json")
                .content("{\"category\":\"POWER\",\"date\":\"2026-10-29\",\"tickets\":[[1,2,3,4,5,6.5]]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
