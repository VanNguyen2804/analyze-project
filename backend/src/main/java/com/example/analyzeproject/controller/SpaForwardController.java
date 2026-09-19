package com.example.analyzeproject.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Điều hướng các route phía client của Angular (như /analyze, /landing, /entry)
 * về trang index.html để Angular Router tự xử lý view.
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/analyze",
            "/analyze/**",
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
