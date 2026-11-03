package com.example.analyzeproject.infrastructure.marketdata;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.example.analyzeproject.domain.service.stock.StockLearningEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit read-only diagnostic; not a network dependency of the normal test suite. */
@EnabledIfSystemProperty(named="stocks.liveProbe",matches="true")
class StockLiveProbeTest {
    @Test void probeSourceAndMeasureActualClassificationWithoutClaimingExecutableReturns() throws Exception {
        var json=new ObjectMapper();var source=new EntradeStockAdapter(json,Clock.systemUTC(),true,1,"UNKNOWN",false);
        var engine=new StockLearningEngine(.6,.6,.004);List<Map<String,Object>> report=new ArrayList<>();
        for(String symbol:List.of("VIC","FPT","SZC","MBB","VNINDEX")){
            for(Mode mode:symbol.equals("FPT")?List.of(Mode.SWING,Mode.INTRADAY):List.of(Mode.SWING)){
                Feed feed=source.load(symbol,mode);var closed=feed.candles().stream().filter(Candle::closed).toList();
                Map<String,Object> item=new LinkedHashMap<>();item.put("symbol",symbol);item.put("mode",mode.name());item.put("bars",feed.candles().size());
                item.put("closedBars",closed.size());item.put("warnings",feed.warnings());
                item.put("lastBarAt",closed.isEmpty()?null:closed.get(closed.size()-1).time());
                item.put("learning",engine.analyze(closed,mode));report.add(item);
            }
        }
        Path out=Path.of("docs/reports/stocks-development-2026-11-03.json");Files.createDirectories(out.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),Map.of("source","DNSE/Entrade","priceBasis","UNKNOWN","verified",false,"results",report));
        assertTrue(report.stream().anyMatch(row->((Number)row.get("bars")).intValue()>300),"No substantial data returned; inspect diagnostic report");
    }
}
