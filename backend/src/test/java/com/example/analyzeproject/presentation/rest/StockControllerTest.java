package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.application.service.StockService;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.example.analyzeproject.domain.service.stock.StockLearningEngine;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StockControllerTest {
    @Test void planContractUsesCanonicalJsonAndRejectsInvalidRiskSettings() throws Exception {
        var clock=Clock.fixed(Instant.parse("2026-11-06T09:00:00Z"),ZoneOffset.UTC);
        var candle=new Candle(clock.instant().minusSeconds(3600).getEpochSecond(),27000,27500,26500,27000,100000,true);
        var service=new StockService((symbol,mode)->new Feed(List.of(candle),clock.instant(),"TEST","VND","RAW",true,List.of()),clock,new StockLearningEngine(.6,.6,.004));
        var snapshot=service.analyze("FPT",Mode.SWING);
        var mvc=MockMvcBuilders.standaloneSetup(new StockController(service, org.mockito.Mockito.mock(com.example.analyzeproject.application.service.PageJobService.class))).build();
        String body="""
            {"purchasePrice":30000,"quantity":1000,"maxLossPercent":15,"sellableQuantity":800,
             "plan":{"startedAt":"2026-11-06T01:00:00Z","executions":[],"observedPeak":null,"availableQuantity":null,
             "policy":{"alertLossPercent":5,"maxLossVnd":6000000,"additionalBudget":10000000,"maxCapital":40000000,
             "averagingBudgets":[5000000,5000000],"targets":[{"gainPercent":10,"price":null,"sellPercent":30},{"gainPercent":20,"price":null,"sellPercent":30}],"trailingPercent":null}}}
            """;
        mvc.perform(post("/api/stocks/FPT/position-analysis").param("snapshotId",snapshot.snapshotId()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.positionPlan.originalStop").value(25500))
                .andExpect(jsonPath("$.positionPlan.quantity").value(1000))
                .andExpect(jsonPath("$.positionPlan.valuationPrice").value(27000));
        mvc.perform(post("/api/stocks/FPT/position-analysis").param("snapshotId",snapshot.snapshotId()).contentType("application/json").content(body.replace("\"alertLossPercent\":5","\"alertLossPercent\":20")))
                .andExpect(status().isBadRequest());
    }
    private StockService service(){
        Clock clock=Clock.fixed(Instant.parse("2026-11-03T09:30:00Z"),ZoneOffset.UTC);
        return new StockService((symbol,mode)->new Feed(List.of(),clock.instant(),"TEST","SOURCE","UNKNOWN",false,List.of()),clock,new StockLearningEngine(.6,.6,.004));
    }
    @Test void emptyDataSerializesExplicitUnavailableInsteadOfInventingPrices() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new StockController(service(), org.mockito.Mockito.mock(com.example.analyzeproject.application.service.PageJobService.class))).build();
        mvc.perform(get("/api/stocks/FPT/analysis").param("mode","SWING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("FPT")).andExpect(jsonPath("$.data.candles").isEmpty())
                .andExpect(jsonPath("$.analysis.signal").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.analysis.learning.accuracy").isEmpty());
        mvc.perform(get("/api/stocks/OTHER/analysis")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/stocks/FPT/analysis").param("mode","UNKNOWN")).andExpect(status().isBadRequest());
    }
    @Test void positionResponsesAreNotCacheableAndUnknownSnapshotIsRejected() throws Exception {
        var stocks=service();String id=stocks.analyze("FPT",Mode.SWING).snapshotId();
        var mvc=MockMvcBuilders.standaloneSetup(new StockController(stocks, org.mockito.Mockito.mock(com.example.analyzeproject.application.service.PageJobService.class))).build();
        String body="{\"purchaseDate\":\"2026-10-01\",\"purchasePrice\":20000}";
        mvc.perform(post("/api/stocks/FPT/position-analysis").param("snapshotId",id).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
        mvc.perform(post("/api/stocks/FPT/position-analysis").param("snapshotId","expired").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }
}
