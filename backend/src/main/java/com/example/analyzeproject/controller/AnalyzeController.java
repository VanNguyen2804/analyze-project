package com.example.analyzeproject.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/analyze")
@CrossOrigin(origins = "*")
public class AnalyzeController {

    @GetMapping("/predict")
    public ResponseEntity<List<Integer>> predictNumbers(@RequestParam(required = false, defaultValue = "MEGA") String category) {
        int maxLimit = "POWER".equalsIgnoreCase(category) ? 55 : 45;
        List<Candidate> candidates = new ArrayList<>();
        Random random = new Random();

        for (int i = 1; i <= maxLimit; i++) {
            double f1 = random.nextDouble();
            double f2 = random.nextDouble();
            double z = f1 * 1.5 - f2 * 0.8 + Math.sin(i) * 0.2;
            double prob = 1.0 / (1.0 + Math.exp(-z));
            candidates.add(new Candidate(i, prob));
        }

        candidates.sort((a, b) -> Double.compare(b.score, a.score));

        List<Integer> top6 = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            top6.add(candidates.get(i).number);
        }
        Collections.sort(top6);

        return ResponseEntity.ok(top6);
    }

    private static class Candidate {
        int number;
        double score;

        Candidate(int number, double score) {
            this.number = number;
            this.score = score;
        }
    }
}
