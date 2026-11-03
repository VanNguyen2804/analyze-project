package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.example.analyzeproject.domain.service.stock.StockDailyDesk;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockDailyDeskTest {
    private List<Candle> bars(int size){List<Candle> bars=new ArrayList<>();long start=Instant.parse("2025-01-01T02:00:00Z").getEpochSecond();for(int i=0;i<size;i++)bars.add(new Candle(start+i*86400L,100+i,102+i,98+i,100+i,1000+i,true));return bars;}
    private Feed feed(List<Candle> bars){return new Feed(bars,Instant.parse("2026-11-04T00:00:00Z"),"TEST","VND","UNKNOWN",false,List.of());}
    @Test void cloudUsesThe26BarDisplacementAndFibonacciAnchorsExcludeTheEvaluatedCandle(){
        var rows=bars(231);var day=StockDailyDesk.build(feed(rows),1.2).days().get(0);
        assertEquals(295.75,day.values().cloudA());assertEquals(278.5,day.values().cloudB());
        assertEquals(326,day.values().tenkan());assertEquals(317.5,day.values().kijun());
        assertEquals(268,day.values().rangeLow());assertEquals(331,day.values().rangeHigh());
        assertEquals(306.934,day.values().fibonacci().get(1).price(),1e-9);
        assertEquals(6,day.readings().size());assertEquals(10,StockDailyDesk.build(feed(rows),1.2).days().size());
    }
    @Test void oldDailyReviewsDoNotChangeWhenFutureBarsAreAppendedOrCurrentBarIsForming(){
        var prefix=bars(220);var original=StockDailyDesk.build(feed(prefix),1.2).days().get(0);
        var full=bars(225);var fromFull=StockDailyDesk.build(feed(full),1.2).days().stream().filter(d->d.date().equals(original.date())).findFirst().orElseThrow();
        assertEquals(original,fromFull);
        var forming=new ArrayList<>(prefix);forming.add(new Candle(prefix.get(219).time()+86400,300,999,200,900,100000,false));
        assertEquals(StockDailyDesk.build(feed(prefix),1.2),StockDailyDesk.build(feed(forming),1.2));
    }
    @Test void wilderRsiMatchesHandCalculatedReferenceAndDoesNotCreateValuesForMissingHistory(){
        double[] closes={44.34,44.09,44.15,43.61,44.33,44.83,45.10,45.42,45.84,46.08,45.89,46.03,45.61,46.28,46.28};
        List<Candle> rows=new ArrayList<>();for(int i=0;i<closes.length;i++)rows.add(new Candle(100+i,closes[i],closes[i]+1,closes[i]-1,closes[i],100,true));
        assertEquals(70.464135021,StockDailyDesk.wilderRsi(rows,14),1e-8);
        assertTrue(StockDailyDesk.build(feed(List.of()),1.2).days().isEmpty());
        var shortHistory=StockDailyDesk.build(feed(bars(60)),1.2).days().get(0);
        assertNull(shortHistory.values().ema200());assertNull(shortHistory.values().cloudB());assertTrue(shortHistory.values().fibonacci().isEmpty());
    }
}
