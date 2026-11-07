package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.application.service.PredictionBenchmark;
import com.example.analyzeproject.application.service.PredictionResearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/prediction-research")
public class PredictionResearchController {
    private final PredictionResearchService research;
    public PredictionResearchController(PredictionResearchService research) { this.research = research; }
    @GetMapping("/inventory") public ResponseEntity<?> inventory(@RequestParam(defaultValue="MEGA") String category) {
        try { return ResponseEntity.ok(research.inventory(category.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }
    @PostMapping("/benchmark") public ResponseEntity<?> benchmark(@RequestParam(defaultValue="MEGA") String category,
            @RequestBody(required=false) PredictionBenchmark.Options options) {
        try { return ResponseEntity.ok(research.benchmark(category.toUpperCase(Locale.ROOT), options == null ? PredictionBenchmark.Options.defaults() : options)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }
}
