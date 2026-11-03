package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.example.analyzeproject.domain.service.stock.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockRecommendationTest {
    private final StockRecommendationEngine engine=new StockRecommendationEngine(StockRecommendationEngine.Policy.defaults());
    private final Learning learning=new StockLearningEngine(.6,.6,.004).analyze(List.of(),Mode.SWING);
    private List<Candle> bars(double close,double high,double volume) {
        List<Candle> bars=new ArrayList<>();long last=Instant.parse("2026-11-03T02:15:00Z").getEpochSecond();
        for(int i=0;i<54;i++){double price=i%2==0?99:101;bars.add(new Candle(last-(54-i)*86400L,price,101.5,98.5,price,100,true));}
        bars.add(new Candle(last,101,Math.max(high,Math.max(close,101)),Math.min(98.5,close-1),close,volume,true));return bars;
    }
    private Feed feed(List<Candle> bars,boolean verified,String basis){return new Feed(bars,Instant.parse("2026-11-03T09:30:00Z"),"TEST","VND",basis,verified,List.of());}
    @Test void confirmedBreakoutProducesPriceConditionsWithoutInterpretingMlAsProbability() {
        var result=engine.evaluate(feed(bars(103,103.5,150),true,"RAW"),Mode.SWING,"RECENT",learning);
        assertEquals("BUY",result.action());assertTrue(result.buyAbove()>101.5);assertTrue(result.buyAbove()<103);
        assertEquals(1.5,result.volumeRatio());assertTrue(result.stopLoss()<103);assertTrue(result.takeProfit()>103);
        assertTrue(result.cautions().stream().anyMatch(c->c.contains("không phải xác suất AI")));
    }
    @Test void lowVolumeWicksAndExtendedBreakoutsCannotTriggerBuy() {
        assertEquals("WATCH",engine.evaluate(feed(bars(103,103.5,90),true,"RAW"),Mode.SWING,"RECENT",learning).action());
        var wick=engine.evaluate(feed(bars(101,108,150),true,"RAW"),Mode.SWING,"RECENT",learning);
        assertEquals("WATCH",wick.action());assertTrue(wick.summary().contains("vượt giả"));
        var extended=engine.evaluate(feed(bars(110,111,150),true,"RAW"),Mode.SWING,"RECENT",learning);
        assertEquals("WATCH",extended.action());assertTrue(extended.title().contains("kiểm tra lại"));
    }
    @Test void breakdownSuggestsReducingAnExistingPositionButUnverifiedOrStaleDataRemainConditional() {
        var result=engine.evaluate(feed(bars(95,101,150),true,"RAW"),Mode.SWING,"RECENT",learning);
        assertEquals("SELL",result.action());assertTrue(result.evaluatedPrice()<result.sellBelow());
        assertEquals("WATCH",engine.evaluate(feed(bars(95,101,150),false,"UNKNOWN"),Mode.SWING,"RECENT",learning).action());
        assertEquals("WATCH",engine.evaluate(feed(bars(103,103.5,150),true,"RAW"),Mode.SWING,"STALE",learning).action());
    }
    @Test void formingCandlesNeverConfirmABreakoutAndThresholdsExcludeTheEvaluatedBar() {
        var original=bars(103,103.5,150);var result=engine.evaluate(feed(original,true,"RAW"),Mode.SWING,"RECENT",learning);
        var withForming=new ArrayList<>(original);withForming.add(new Candle(original.get(54).time()+86400,103,200,100,190,10000,false));
        assertEquals(result,engine.evaluate(feed(withForming,true,"RAW"),Mode.SWING,"RECENT",learning));
        assertTrue(result.buyAbove()<original.get(54).high());
    }
    @Test void intradayVolumeUsesPriorSessionsAtTheSameTimeAndMissingDataDoesNotInventLevels() {
        var result=engine.evaluate(feed(bars(103,103.5,150),true,"RAW"),Mode.INTRADAY,"RECENT",learning);
        assertEquals(1.5,result.volumeRatio());
        var onlySameDay=new ArrayList<Candle>();long start=Instant.parse("2026-11-03T02:00:00Z").getEpochSecond();
        for(int i=0;i<55;i++)onlySameDay.add(new Candle(start+i*60,100,101,99,100,100,true));
        assertNull(engine.evaluate(feed(onlySameDay,true,"RAW"),Mode.INTRADAY,"RECENT",learning).volumeRatio());
        var missing=engine.evaluate(feed(List.of(),true,"RAW"),Mode.SWING,"RECENT",learning);
        assertEquals("UNAVAILABLE",missing.action());assertNull(missing.buyAbove());
        assertThrows(IllegalArgumentException.class,()->new StockRecommendationEngine.Policy(.25,Double.NaN,1.5,3,70));
    }
}
