package com.example.analyzeproject.presentation.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Điều hướng các route phía client của Angular (như /analyze, /landing, /entry)
 * về trang index.html để Angular Router tự xử lý view.
 */
@ConditionalOnProperty(name = "app.frontend.enabled", havingValue = "true")
@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/analyze",
            "/ai-lab",
            "/latest-analysis",
            "/latest-analysis/**",
            "/landing",
            "/landing/**",
            "/entry",
            "/entry/**",
            "/manual",
            "/manual/**"
    })
    public String forwardFrontendRoutes() {
        return "forward:/index.html";
    }
}
