package com.example.analyzeproject.presentation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Explicit browser origins for the independently hosted Angular application. */
@Configuration
public class ApiCorsConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public ApiCorsConfig(@Value("${app.cors.allowed-origins:http://localhost:4200}") String origins) {
        allowedOrigins = java.util.Arrays.stream(origins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept")
                .maxAge(3600);
    }
}
