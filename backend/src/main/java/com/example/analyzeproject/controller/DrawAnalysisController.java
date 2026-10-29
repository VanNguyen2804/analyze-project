package com.example.analyzeproject.controller;

import com.example.analyzeproject.dto.DrawReport;
import com.example.analyzeproject.dto.DrawReport.*;
import com.example.analyzeproject.service.DrawAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
public class DrawAnalysisController {
    private final DrawAnalysisService analysis;
    public DrawAnalysisController(DrawAnalysisService analysis) { this.analysis=analysis; }
    @GetMapping("/draw-report") public DrawReport report(@RequestParam(defaultValue="MEGA") String category,
            @RequestParam(required=false) String date,@RequestParam(defaultValue="temporal_learning") String algorithm,
            @RequestParam(required=false) String evidenceId) { return analysis.report(category,date,algorithm,evidenceId); }
    @PostMapping("/evaluate-tickets") public TicketGroup evaluate(@RequestBody EvaluateRequest request) { return analysis.evaluateTickets(request); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }
}
