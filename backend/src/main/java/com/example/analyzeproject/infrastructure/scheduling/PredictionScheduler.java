package com.example.analyzeproject.infrastructure.scheduling;

import com.example.analyzeproject.application.service.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduling is an input adapter; application use cases have no Spring lifecycle. */
@Component
public class PredictionScheduler {
    private final ForecastService forecasts;
    private final AiLabService lab;
    private final WorkspaceService workspace;
    private final MegaPortfolioService portfolio;

    public PredictionScheduler(ForecastService forecasts, AiLabService lab,
            WorkspaceService workspace, MegaPortfolioService portfolio) {
        this.forecasts = forecasts;
        this.lab = lab;
        this.workspace = workspace;
        this.portfolio = portfolio;
    }

    @Scheduled(initialDelay = 12000, fixedDelay = 60000)
    public void forecasts() { forecasts.tick(); }

    @Scheduled(initialDelay = 20000, fixedDelay = 60000)
    public void lab() { lab.tick(); }

    @Scheduled(initialDelay = 25000, fixedDelay = 60000)
    public void workspace() { workspace.tick(); }

    @Scheduled(initialDelay = 25000, fixedDelay = 60000)
    public void portfolio() { portfolio.tick(); }
}
