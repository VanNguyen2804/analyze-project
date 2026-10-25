package com.example.analyzeproject.controller;

import com.example.analyzeproject.sync.LotteryResultScheduler;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/lottery-sync")
public class LotterySyncController {
    private final LotteryResultScheduler scheduler;
    public LotterySyncController(LotteryResultScheduler scheduler) { this.scheduler = scheduler; }
    @GetMapping("/status")
    public Map<String, Object> status() { return scheduler.status(); }
}
