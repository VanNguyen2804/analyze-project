package com.example.analyzeproject.infrastructure.config;

import com.example.analyzeproject.application.port.out.*;
import com.example.analyzeproject.application.service.*;
import com.example.analyzeproject.infrastructure.persistence.TransactionalDrawAnalysisService;
import com.example.analyzeproject.infrastructure.persistence.TransactionalLotteryResultImporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;

/** Composition root: only the outer layer knows how application ports are wired. */
@Configuration
public class ApplicationConfig {
    @Bean Clock applicationClock() { return Clock.systemUTC(); }

    @Bean AnalyzeService analyzeService(LotteryNumberRepository draws, UserTicketRepository tickets,
            AlgorithmHyperparameterRepository parameters, LotteryDeviationVariableRepository deviations) {
        return new AnalyzeService(draws, tickets, parameters, deviations);
    }

    @Bean LotteryNumberService lotteryNumberService(LotteryNumberRepository draws) {
        return new LotteryNumberService(draws);
    }

    @Bean DrawQualityService drawQualityService(LotteryNumberRepository draws) {
        return new DrawQualityService(draws);
    }

    @Bean ForecastService forecastService(AnalyzeService analyze, LotteryNumberRepository draws,
            ForecastStore store, @Value("${forecast.enabled:true}") boolean enabled) {
        return new ForecastService(analyze, draws, store, enabled);
    }

    @Bean AiLabService aiLabService(AiLabStore store, LotteryNumberRepository draws,
            @Value("${ai-lab.enabled:true}") boolean enabled) {
        return new AiLabService(store, draws, enabled);
    }

    @Bean WorkspaceService workspaceService(AnalyzeService analyze, LotteryNumberRepository draws,
            AiLabStore lab, WorkspaceStore workspace, Clock clock) {
        return new WorkspaceService(analyze, draws, lab, workspace, clock);
    }

    @Bean MegaPortfolioService megaPortfolioService(AnalyzeService analyze, LotteryNumberRepository draws,
            MegaPortfolioStore store, ForecastStore forecasts, AiLabStore lab, Clock clock,
            @Value("${mega-portfolio.enabled:true}") boolean enabled) {
        return new MegaPortfolioService(analyze, draws, store, enabled, clock, forecasts, lab);
    }

    @Bean DrawAnalysisService drawAnalysisService(LotteryNumberRepository draws, UserTicketRepository users,
            ForecastStore forecasts, AiLabStore lab, WorkspaceStore workspace, Clock clock) {
        return new TransactionalDrawAnalysisService(draws, users, forecasts, lab, workspace, clock);
    }

    @Bean LotteryResultImporter lotteryResultImporter(LotteryNumberRepository draws, JdbcTemplate jdbc) {
        return new TransactionalLotteryResultImporter(draws, jdbc);
    }
}
