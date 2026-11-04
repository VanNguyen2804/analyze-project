package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.example.analyzeproject.domain.service.stock.StockLearningEngine;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockServiceTest {
    @Test void positionNeedsNoPurchaseDateAndUsesFixedTenPercentStopLoss() {
        var bars=history(100); double current=bars.get(99).close();
        var service=service(bars,true,"RAW",clock); var snapshot=service.analyze("FPT",Mode.SWING);
        var result=service.position("FPT",snapshot.snapshotId(),new PositionInput(null,current/.89,200.0,null,null,99.0,null));
        assertEquals(-11,result.changePercent(),1e-8);
        assertEquals((current-current/.89)*200,result.grossPnl(),1e-8);
        assertEquals("SELL",result.status());
        assertTrue(result.reasons().stream().anyMatch(r->r.contains("10%")));
        var above=service.position("FPT",snapshot.snapshotId(),new PositionInput(null,current/.91,200.0,null,null,1.0,null));
        assertTrue(above.reasons().stream().noneMatch(r->r.contains("cắt lỗ cố định")));
    }
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-03T09:30:00Z"),ZoneOffset.UTC);
    private final StockLearningEngine engine=new StockLearningEngine(.60,.60,.004);
    private List<Candle> history(int n) {
        List<Candle> bars=new ArrayList<>();Random random=new Random(73);double price=100;
        long last=Instant.parse("2026-11-03T02:00:00Z").getEpochSecond();
        for(int i=0;i<n;i++){
            double open=price;price=Math.max(1,price*(1+random.nextGaussian()*.02));
            bars.add(new Candle(last-(n-i-1)*86400L,open,Math.max(open,price)*1.01,Math.min(open,price)*.99,price,10000+i*10,true));
        }
        return bars;
    }
    private StockService service(List<Candle> bars,boolean verified,String basis,Clock useClock) {
        return new StockService((symbol,mode)->new Feed(bars,clock.instant(),"TEST","VND",basis,verified,List.of()),useClock,engine);
    }
    @Test void learningIsTrainedReproducibleAndPurgesLabelsBeforeEveryValidationBlock(){
        var bars=history(500);var a=engine.analyze(bars,Mode.SWING);var b=engine.analyze(bars,Mode.SWING);
        assertEquals(a,b);assertTrue(a.trainSamples()>300);assertTrue(a.testSamples()>100);assertEquals(3,a.validationFolds().size());
        for(var fold:a.validationFolds())assertTrue(fold.trainingLabelThrough()<fold.validationFrom());
        assertEquals(1,a.buyScore()+a.holdScore()+a.sellScore(),1e-10);
        assertNotEquals(a.buyScore(),engine.analyze(history(470),Mode.SWING).buyScore());
        assertTrue(a.accuracy()>=0&&a.accuracy()<=1);assertTrue(a.logLoss()>=0);
    }
    @Test void indicatorsCannotSeeFutureBars(){
        var prefix=history(100);var full=new ArrayList<>(prefix);Candle last=prefix.get(99);
        full.add(new Candle(last.time()+86400,10000,10001,9999,10000,5,true));
        assertArrayEquals(StockLearningEngine.features(prefix,99),StockLearningEngine.features(full,99),0);
    }
    @Test void insufficientConstantOrUnverifiedDataCannotCreateActionablePrediction(){
        assertEquals("INSUFFICIENT_DATA",engine.analyze(List.of(),Mode.SWING).status());
        var constant=history(400).stream().map(c->new Candle(c.time(),100,100,100,100,0,true)).toList();
        var learned=engine.analyze(constant,Mode.SWING);assertEquals("VALIDATION_WEAK",learned.status());assertEquals("WATCH",learned.signal());
        var s=service(history(400),false,"UNKNOWN",clock).analyze("FPT",Mode.SWING);assertEquals("UNAVAILABLE",s.analysis().signal());
        var stale=service(history(100),true,"RAW",Clock.offset(clock,Duration.ofDays(5))).analyze("FPT",Mode.SWING);
        assertEquals("STALE",stale.freshness());assertEquals("UNAVAILABLE",stale.analysis().signal());
    }
    @Test void intradayTrainingDoesNotInventLabelsAcrossTradingDates(){
        // One candle per date is intentionally insufficient for a same-day 4-bar target.
        assertEquals("INSUFFICIENT_DATA",engine.analyze(history(400),Mode.INTRADAY).status());
    }
    @Test void duplicateAndUnclosedCandlesRejected(){
        var bars=history(100);var dup=new ArrayList<>(bars);dup.add(bars.get(99));
        assertThrows(IllegalArgumentException.class,()->engine.analyze(dup,Mode.SWING));
        var open=List.of(new Candle(100,1,1,1,1,0,false));assertThrows(IllegalArgumentException.class,()->engine.analyze(open,Mode.SWING));
        assertThrows(IllegalArgumentException.class,()->new Candle(100,1,1,2,1,0,true));
    }
    @Test void positionUsesDisplayedSnapshotAndCalculatesExplicitCosts(){
        var bars=history(100);double current=bars.get(99).close();var service=service(bars,true,"RAW",clock);var snapshot=service.analyze("FPT",Mode.SWING);
        var input=new PositionInput(LocalDate.of(2026,10,1),current/1.1,100.0,.15,.25,5.0,0.0);
        var result=service.position("FPT",snapshot.snapshotId(),input);
        assertEquals("PositionInput[redacted]",input.toString());assertEquals("PositionResult[redacted]",result.toString());
        assertEquals(10,result.changePercent(),1e-8);assertEquals((current-current/1.1)*100,result.grossPnl(),1e-8);
        assertEquals((current*.9975-current/1.1*1.0015)*100,result.estimatedNetPnl(),1e-8);
        assertThrows(IllegalArgumentException.class,()->service.position("MBB",snapshot.snapshotId(),input));
        assertThrows(IllegalArgumentException.class,()->service.position("FPT","expired",input));
    }
    @Test void positionWithoutCostsOrQuantityDoesNotInventNetPnlAndUnknownBasisAllowsReferencePnl(){
        var service=service(history(100),true,"RAW",clock);var s=service.analyze("FPT",Mode.SWING);
        var input=new PositionInput(LocalDate.of(2026,10,1),100.0,null,null,null,null,null);
        var p=service.position("FPT",s.snapshotId(),input);assertNull(p.grossPnl());assertNull(p.estimatedNetPnl());assertNull(p.breakEven());
        var unknown=service(history(100),false,"UNKNOWN",clock);var u=unknown.analyze("FPT",Mode.SWING);
        var reference=unknown.position("FPT",u.snapshotId(),input);assertEquals("WATCH",reference.status());assertNotNull(reference.changePercent());
        assertTrue(reference.reasons().stream().anyMatch(r->r.contains("tham khảo")));
        var invalid=new PositionInput(LocalDate.of(2027,1,1),Double.NaN,null,null,null,null,null);
        assertThrows(IllegalArgumentException.class,()->service.position("FPT",s.snapshotId(),invalid));
        assertThrows(IllegalArgumentException.class,()->service.analyze("OTHER",Mode.SWING));
    }
    @Test void screenshotPositionWorksWithoutFeesAndThousandDongIsConvertedExactlyOnce(){
        var bars=List.of(new Candle(Instant.parse("2026-11-03T02:00:00Z").getEpochSecond(),230,235,229,234,1000,true));
        var service=new StockService((symbol,mode)->new Feed(bars,clock.instant(),"DNSE / Entrade","THOUSAND_VND","UNKNOWN",false,List.of()),clock,engine);
        var snapshot=service.analyze("VIC",Mode.SWING);
        var input=new PositionInput(LocalDate.of(2026,11,3),234000.0,200.0,null,null,10.0,200.0);
        var result=service.position("VIC",snapshot.snapshotId(),input);
        assertEquals(234000,result.price());assertEquals(0,result.changePercent());assertEquals(0,result.grossPnl());assertEquals("WATCH",result.status());
        assertNull(result.estimatedNetPnl());assertTrue(result.reasons().stream().noneMatch(r->r.contains("thiếu đầu vào")));
        var changed=new PositionInput(LocalDate.of(2026,11,3),230000.0,200.0,null,null,10.0,200.0);
        assertEquals(800000,service.position("VIC",snapshot.snapshotId(),changed).grossPnl());
    }
    @Test void staleDataRemainsExplicitReferenceButAdjustedOrUnknownUnitsCannotBeCompared(){
        var stale=service(history(100),false,"UNKNOWN",Clock.offset(clock,Duration.ofDays(5)));
        var snapshot=stale.analyze("FPT",Mode.SWING);var input=new PositionInput(LocalDate.of(2026,10,1),100.0,200.0,null,null,10.0,null);
        var result=stale.position("FPT",snapshot.snapshotId(),input);assertNotNull(result.grossPnl());assertEquals("WATCH",result.status());
        assertTrue(result.reasons().stream().anyMatch(r->r.contains("Dữ liệu cũ")));
        var adjusted=service(history(100),true,"ADJUSTED",clock);var a=adjusted.analyze("FPT",Mode.SWING);
        assertEquals("UNAVAILABLE",adjusted.position("FPT",a.snapshotId(),input).status());
        var unknown=new StockService((symbol,mode)->new Feed(history(100),clock.instant(),"TEST","SOURCE","UNKNOWN",false,List.of()),clock,engine);var u=unknown.analyze("FPT",Mode.SWING);
        assertNull(unknown.position("FPT",u.snapshotId(),input).price());
    }
}
