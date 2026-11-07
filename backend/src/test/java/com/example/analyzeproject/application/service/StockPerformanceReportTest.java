package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.example.analyzeproject.domain.service.stock.StockLearningEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

class StockPerformanceReportTest {
    static List<Candle> candles(int count) {
        Random random=new Random(7441);var bars=new ArrayList<Candle>();double price=100;
        long last=Instant.parse("2026-11-06T08:00:00Z").getEpochSecond();
        for(int i=0;i<count;i++){double open=price;price=Math.max(1,price*(1+random.nextGaussian()*.015));
            bars.add(new Candle(last-(count-i-1)*86400L,open,Math.max(open,price)*1.01,Math.min(open,price)*.99,price,10000+i*11,true));}
        return bars;
    }
    @Test @EnabledIfSystemProperty(named="performance.report.path",matches=".+")
    void profileTrainingAndOptionallyCaptureReference() throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();var engine=new StockLearningEngine(.6,.6,.004);
        var bars=candles(1250);engine.analyze(bars,Mode.SWING);
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
        var result=engine.analyze(bars,Mode.SWING);long elapsed=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-before;
        var report=Map.of("candles",bars.size(),"milliseconds",elapsed/1_000_000.0,"allocatedBytes",bytes,"result",result);
        var out=Path.of(System.getProperty("performance.report.path"));Files.createDirectories(out.toAbsolutePath().getParent());json.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),report);
        if(Boolean.getBoolean("performance.captureReference")) {
            var path=Path.of("src/test/resources/performance/stock-learning-reference.json");Files.createDirectories(path.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),result);
        }
        assertTrue(result.trainSamples()>0);
    }
    @Test void optimizedTrainingRetainsTheOriginalModelOutput() throws Exception {
        try(var reference=getClass().getResourceAsStream("/performance/stock-learning-reference.json")) {
            assertNotNull(reference,"Missing pre-optimization model reference");
            var json=new ObjectMapper().findAndRegisterModules();
            var result=new StockLearningEngine(.6,.6,.004).analyze(candles(1250),Mode.SWING);
            assertEquals(json.readTree(reference),json.readTree(json.writeValueAsBytes(result)));
        }
    }
}
