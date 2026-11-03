package com.example.analyzeproject.infrastructure.marketdata;

import com.example.analyzeproject.application.service.StockService;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.example.analyzeproject.domain.service.stock.StockLearningEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Optional local UI diagnostic from an actual downloaded public provider response; never used as live application data. */
@EnabledIfSystemProperty(named="stocks.visualProbe",matches="true")
class StockVisualProbeTest {
    @Test void prepareReadOnlyPreviewFromCapturedProviderData() throws Exception {
        Path folder=Path.of(System.getProperty("java.io.tmpdir"),"codex-stock-diagnostic");var json=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var root=json.readTree(folder.resolve("data.json").toFile());List<Candle> bars=new ArrayList<>();
        for(int i=0;i<root.path("t").size();i++)bars.add(new Candle(root.path("t").get(i).asLong(),root.path("o").get(i).asDouble()*1000,root.path("h").get(i).asDouble()*1000,root.path("l").get(i).asDouble()*1000,root.path("c").get(i).asDouble()*1000,root.path("v").get(i).asDouble(),true));
        bars=EntradeStockAdapter.normalizeDaily(bars);assertTrue(bars.size()>500);var captured=List.copyOf(bars);Clock clock=Clock.systemUTC();
        var service=new StockService((symbol,mode)->new Feed(symbol.equals("VIC")?captured:List.of(),FilesTime(folder),"DNSE / Entrade (bản xem thử dữ liệu đã tải)",symbol.equals("VIC")?"VND":"POINTS","UNKNOWN",false,List.of("Bản xem thử dùng dữ liệu thực đã tải, không phải giá trực tiếp.")),clock,new StockLearningEngine(.6,.6,.004));
        json.writerWithDefaultPrettyPrinter().writeValue(folder.resolve("snapshot.json").toFile(),service.analyze("VIC",Mode.SWING));
    }
    private static Instant FilesTime(Path folder){try{return Files.getLastModifiedTime(folder.resolve("data.json")).toInstant();}catch(Exception e){throw new IllegalStateException(e);}}
}
