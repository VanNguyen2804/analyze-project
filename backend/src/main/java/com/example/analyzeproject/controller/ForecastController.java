package com.example.analyzeproject.controller;

import com.example.analyzeproject.service.ForecastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/forecasts")
@CrossOrigin(origins = "*")
public class ForecastController {
    private final ForecastService forecasts;
    public ForecastController(ForecastService forecasts) { this.forecasts = forecasts; }
    @GetMapping public Map<String,Object> list() { return forecasts.list(); }
    @GetMapping("/overview") public ResponseEntity<?> overview(@RequestParam(defaultValue="MEGA") String category,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        try { return ResponseEntity.ok(forecasts.overview(category.toUpperCase(Locale.ROOT),page,size)); }
        catch(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }
    }
    @PostMapping("/capture") public Map<String,Object> capture() { return forecasts.captureAll(); }
    @PostMapping("/reconcile") public Map<String,Object> reconcile() { return forecasts.reconcile(); }
    @GetMapping("/evaluation") public ResponseEntity<?> evaluation(@RequestParam(required=false) String category) {
        try { return ResponseEntity.ok(forecasts.evaluation(category == null ? null : category.toUpperCase(Locale.ROOT))); }
        catch(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }
    }
}
