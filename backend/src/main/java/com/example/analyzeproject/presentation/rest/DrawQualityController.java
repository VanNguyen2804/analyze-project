package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.domain.service.*;

import com.example.analyzeproject.application.service.DrawQualityService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/lottery-sync")
public class DrawQualityController {
    private final DrawQualityService service;
    public DrawQualityController(DrawQualityService service) { this.service = service; }
    @GetMapping("/quality")
    public Map<String,Object> quality() {
        return service.quality();
    }
}
