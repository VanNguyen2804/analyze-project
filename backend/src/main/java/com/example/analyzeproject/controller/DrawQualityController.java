package com.example.analyzeproject.controller;

import com.example.analyzeproject.repository.LotteryNumberRepository;
import com.example.analyzeproject.service.DrawQuality;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/lottery-sync")
public class DrawQualityController {
    private final LotteryNumberRepository repository;
    public DrawQualityController(LotteryNumberRepository repository) { this.repository = repository; }
    @GetMapping("/quality")
    public Map<String,Object> quality() {
        var rows = repository.findAllByOrderByDrawDateDescCreatedAtDesc();
        long eligible = DrawQuality.history(rows).size();
        return Map.of("total",rows.size(),"eligible",eligible,"excluded",rows.size()-eligible,"records",rows);
    }
}
