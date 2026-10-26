package com.example.analyzeproject.controller;
import com.example.analyzeproject.service.AiLabService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/ai-lab")
@CrossOrigin(origins="*")
public class AiLabController {
    private final AiLabService lab;
    public AiLabController(AiLabService lab) { this.lab=lab; }
    @GetMapping public ResponseEntity<?> overview(@RequestParam(defaultValue="MEGA") String category,@RequestParam(defaultValue="0") int page) {
        try { return ResponseEntity.ok(lab.overview(category.toUpperCase(Locale.ROOT),page)); }
        catch(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }
    }
    @PostMapping("/run") public Map<String,Object> run() { return lab.run(); }
}
