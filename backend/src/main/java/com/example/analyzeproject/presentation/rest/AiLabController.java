package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.domain.service.*;
import com.example.analyzeproject.application.service.AiLabService;
import com.example.analyzeproject.application.service.PageJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/ai-lab")
public class AiLabController {
    private final AiLabService lab;
    private final PageJobService jobs;
    public AiLabController(AiLabService lab,PageJobService jobs) { this.lab=lab;this.jobs=jobs; }
    private String category(String value){String c=value.toUpperCase(Locale.ROOT);if(!Set.of("MEGA","POWER").contains(c))throw new IllegalArgumentException("Invalid category");return c;}
    @GetMapping public ResponseEntity<?> overview(@RequestParam(defaultValue="MEGA") String category,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="false") boolean async) {
        try { if(page<0)throw new IllegalArgumentException("Invalid page");String c=category(category);return async?PageJobController.response(jobs.submit("LAB_OVERVIEW",Map.of("category",c,"page",page))):ResponseEntity.ok(lab.overview(c,page)); }
        catch(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }
    }
    @GetMapping("/highlights") public ResponseEntity<?> highlights(@RequestParam(defaultValue="MEGA") String category,@RequestParam(defaultValue="false") boolean async) {
        try { String c=category(category);return async?PageJobController.response(jobs.submit("LAB_HIGHLIGHTS",Map.of("category",c))):ResponseEntity.ok(lab.highlights(c)); }
        catch(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }
    }
    @GetMapping("/predictions") public ResponseEntity<?> predictions(@RequestParam(defaultValue="MEGA") String category,@RequestParam(defaultValue="false") boolean async) {
        try { String c=category(category);return async?PageJobController.response(jobs.submit("LAB_PREDICTIONS",Map.of("category",c))):ResponseEntity.ok(lab.predictions(c)); }
        catch(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }
    }
    @PostMapping("/run") public Object run(@RequestParam(defaultValue="false") boolean async) { return async?PageJobController.response(jobs.submit("LAB_RUN",Map.of())):lab.run(); }
}
