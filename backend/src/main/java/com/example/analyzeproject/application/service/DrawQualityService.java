package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.LotteryNumberRepository;
import com.example.analyzeproject.domain.service.DrawQuality;
import java.util.Map;

public class DrawQualityService {
    private final LotteryNumberRepository draws;
    public DrawQualityService(LotteryNumberRepository draws) { this.draws = draws; }

    public Map<String,Object> quality() {
        var rows = draws.findAllByOrderByDrawDateDescCreatedAtDesc();
        long eligible = DrawQuality.history(rows).size();
        return Map.of("total", rows.size(), "eligible", eligible, "excluded", rows.size() - eligible, "records", rows);
    }
}
