package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.application.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LabExperimentsHttpTest {
    @Test void experimentReadUsesSavedRowsAndValidatesCategory() throws Exception {
        var lab=mock(AiLabService.class);var jobs=mock(PageJobService.class);
        when(lab.experiments("MEGA")).thenReturn(List.of(Map.of("id","saved","numbers",List.of(1,2,3,4,5,6))));
        var mvc=MockMvcBuilders.standaloneSetup(new AiLabController(lab,jobs)).build();
        mvc.perform(get("/api/ai-lab/experiments").param("category","mega")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("saved"));
        mvc.perform(get("/api/ai-lab/experiments").param("category","OTHER")).andExpect(status().isBadRequest());
        verify(lab).experiments("MEGA");verifyNoMoreInteractions(lab);verifyNoInteractions(jobs);
    }
}
