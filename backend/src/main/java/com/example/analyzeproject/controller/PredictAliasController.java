package com.example.analyzeproject.controller;

import com.example.analyzeproject.dto.PredictionResponseDto;
import com.example.analyzeproject.service.AnalyzeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
public class PredictAliasController {

    private final AnalyzeService analyzeService;

    @Autowired
    public PredictAliasController(AnalyzeService analyzeService) {
        this.analyzeService = analyzeService;
    }

    @RequestMapping(value = {"/predict", "/api/predict"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<PredictionResponseDto> predict(
            @RequestParam(required = false, defaultValue = "MEGA") String category,
            @RequestParam(required = false, defaultValue = "xgboost") String algorithm) {
        PredictionResponseDto result = analyzeService.analyzeAndPredict(category, algorithm);
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = {"/reconcile-5-draws", "/api/reconcile-5-draws"})
    public ResponseEntity<?> reconcile5DrawsAlias(
            @RequestParam(value = "category", defaultValue = "MEGA") String category,
            @RequestParam(value = "algorithm", defaultValue = "deep_stacking") String algorithm,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @RequestParam(value = "date", required = false) String date) {
        return ResponseEntity.ok(analyzeService.reconcileDraws(category, algorithm, limit, date));
    }
}
