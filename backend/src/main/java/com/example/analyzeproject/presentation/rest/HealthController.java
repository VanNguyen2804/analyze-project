package com.example.analyzeproject.presentation.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** Liveness only: external feeds and history queries must not determine process health. */
@RestController
public class HealthController {
    @GetMapping("/api/health") public Map<String,String> health(){return Map.of("status","UP");}
}
