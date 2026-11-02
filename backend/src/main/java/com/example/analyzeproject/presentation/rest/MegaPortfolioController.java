package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.domain.service.*;

import com.example.analyzeproject.application.service.MegaPortfolioService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.Map;

@RestController
@RequestMapping("/api/mega-portfolio")
@CrossOrigin(origins="*")
public class MegaPortfolioController {
    private final MegaPortfolioService service;
    public MegaPortfolioController(MegaPortfolioService service) { this.service=service; }
    @GetMapping public ResponseEntity<?> overview(@RequestParam(defaultValue="0") int page) {
        try { return ResponseEntity.ok(service.overview(page)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }
    }
    @PostMapping("/run") public Map<String,Object> run() { return service.run(); }
}
