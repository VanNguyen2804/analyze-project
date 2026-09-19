package com.example.analyzeproject.controller;

import com.example.analyzeproject.service.AnalyzeService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/analyze")
@CrossOrigin(origins = "*")
public class AnalyzeController {
    private final AnalyzeService service;

    public AnalyzeController(AnalyzeService service) {
        this.service = service;
    }

    @GetMapping("/predict")
    public List<Integer> getPrediction() {
        return service.predictNumbers();
    }
}
