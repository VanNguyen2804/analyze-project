package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.application.port.in.LotterySyncStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/lottery-sync")
public class LotterySyncController {
    private final LotterySyncStatus scheduler;
    public LotterySyncController(LotterySyncStatus scheduler) { this.scheduler = scheduler; }
    @GetMapping("/status")
    public Map<String, Object> status() { return scheduler.status(); }
}
