package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.presentation.config.ApiCorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@Import(ApiCorsConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=https://analyze-frontend.onrender.com,http://localhost:4200")
class ApiCorsTest {
    @Autowired MockMvc http;

    @Test void allowsConfiguredFrontendToReadApi() throws Exception {
        http.perform(get("/api/health").header("Origin", "https://analyze-frontend.onrender.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://analyze-frontend.onrender.com"));
    }

    @Test void permitsJsonWritePreflightWithoutExecutingAMutation() throws Exception {
        http.perform(options("/api/health")
                        .header("Origin", "https://analyze-frontend.onrender.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://analyze-frontend.onrender.com"));
    }

    @Test void rejectsOtherBrowserOrigins() throws Exception {
        http.perform(get("/api/health").header("Origin", "https://unrelated.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test void backendDoesNotForwardAngularRoutesByDefault() throws Exception {
        http.perform(get("/landing")).andExpect(status().isNotFound());
    }
}
