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
    private StockService service(){
        Clock clock=Clock.fixed(Instant.parse("2026-11-03T09:30:00Z"),ZoneOffset.UTC);
        return new StockService((symbol,mode)->new Feed(List.of(),clock.instant(),"TEST","SOURCE","UNKNOWN",false,List.of()),clock,new StockLearningEngine(.6,.6,.004));
    }
    @Test void emptyDataSerializesExplicitUnavailableInsteadOfInventingPrices() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new StockController(service())).build();
        mvc.perform(get("/api/stocks/FPT/analysis").param("mode","SWING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("FPT")).andExpect(jsonPath("$.data.candles").isEmpty())
                .andExpect(jsonPath("$.analysis.signal").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.analysis.learning.accuracy").isEmpty());
        mvc.perform(get("/api/stocks/OTHER/analysis")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/stocks/FPT/analysis").param("mode","UNKNOWN")).andExpect(status().isBadRequest());
    }
    @Test void positionResponsesAreNotCacheableAndUnknownSnapshotIsRejected() throws Exception {
        var stocks=service();String id=stocks.analyze("FPT",Mode.SWING).snapshotId();
        var mvc=MockMvcBuilders.standaloneSetup(new StockController(stocks)).build();
        String body="{\"purchaseDate\":\"2026-10-01\",\"purchasePrice\":20000}";
        mvc.perform(post("/api/stocks/FPT/position-analysis").param("snapshotId",id).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
        mvc.perform(post("/api/stocks/FPT/position-analysis").param("snapshotId","expired").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }
}
