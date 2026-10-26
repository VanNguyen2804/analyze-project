package com.example.analyzeproject.service;

import com.example.analyzeproject.controller.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiLabHttpTest {
    @Test void directPageNavigationForwardsToAngular() throws Exception {
        MockMvcBuilders.standaloneSetup(new SpaForwardController()).build().perform(get("/ai-lab"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }
    @Test void getOnlyReadsAndInvalidQueriesReturnBadRequest() throws Exception {
        var service=mock(AiLabService.class);
        when(service.overview("MEGA",0)).thenReturn(Map.of("totalRows",0));
        when(service.overview("INVALID",0)).thenThrow(new IllegalArgumentException("Invalid category or page"));
        var http=MockMvcBuilders.standaloneSetup(new AiLabController(service)).build();
        http.perform(get("/api/ai-lab")).andExpect(status().isOk()).andExpect(jsonPath("$.totalRows").value(0));
        http.perform(get("/api/ai-lab").param("category","invalid")).andExpect(status().isBadRequest());
        verify(service,never()).run();
    }
    @Test void runRequiresPost() throws Exception {
        var service=mock(AiLabService.class); when(service.run()).thenReturn(Map.of("status","COMPLETED"));
        MockMvcBuilders.standaloneSetup(new AiLabController(service)).build().perform(post("/api/ai-lab/run"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        verify(service).run();
    }
}
