package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.example.analyzeproject.domain.service.stock.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockCandleScenariosTest {
    private final Instant now=Instant.parse("2026-11-06T09:00:00Z");
    private List<Candle> bars(int count) {
        List<Candle> result=new ArrayList<>();
        for(int i=0;i<count;i++){double close=90+i*.1+Math.sin(i*.6);result.add(new Candle(now.minusSeconds(3600+(count-i-1)*86400L).getEpochSecond(),close-.2,close+1,close-1,close,10000+i*100,true));}
        return result;
    }
    private StockService service(List<Candle> bars,boolean verified,String basis,String unit,Instant current) {
        return new StockService((symbol,mode)->new Feed(bars,now,"TEST",unit,basis,verified,List.of()),Clock.fixed(current,ZoneOffset.UTC),new StockLearningEngine(.6,.6,.004));
    }
    private PositionInput position(){return new PositionInput(null,110.0,200.0,null,null,null,null);}
    @Test void twoInputsProduceBuyAndSellAlternativesWithoutInventingFundsOrSellability() {
        var service=service(bars(100),true,"RAW","VND",now);var snapshot=service.analyze("FPT",Mode.SWING);
        var result=service.position("FPT",snapshot.snapshotId(),position());var p=result.scenarios();assertNotNull(p);
        assertEquals(snapshot.snapshotId(),p.snapshotId());assertNull(result.positionPlan());assertTrue(p.confirmed());
        assertEquals(2,p.items().stream().filter(i->i.side().equals("BUY")).count());assertEquals(3,p.items().stream().filter(i->i.side().equals("SELL")).count());
        var buys=p.items().stream().filter(i->i.side().equals("BUY")).toList();
        buys.forEach(i->{assertEquals(50,i.suggestedQuantity());assertEquals(250,i.quantityAfter());assertEquals(i.triggerPrice()*50,i.additionalCash(),1e-8);assertEquals((110*200+i.additionalCash())/250,i.averageAfter(),1e-8);});
        assertEquals(200,p.items().stream().filter(i->i.id().equals("SELL_RESISTANCE")||i.id().equals("SELL_EXTENSION")).mapToDouble(CandleScenario::suggestedQuantity).sum());
        assertEquals(0,p.items().stream().filter(i->i.id().equals("SELL_EXTENSION")).findFirst().orElseThrow().quantityAfter());
        assertTrue(p.cautions().stream().anyMatch(c->c.contains("Chưa biết ngân sách")));assertTrue(p.cautions().stream().anyMatch(c->c.contains("không đồng nghĩa lượng có thể bán")));
    }
    @Test void staleAndUnverifiedPricesCannotConfirmAnyScenario() {
        for(boolean stale:List.of(false,true)) {
            var service=service(bars(100),stale,"RAW","VND",stale?now.plus(Duration.ofDays(7)):now);var snapshot=service.analyze("FPT",Mode.SWING);
            var p=service.position("FPT",snapshot.snapshotId(),position()).scenarios();assertNotNull(p);assertFalse(p.confirmed());assertEquals("REFERENCE",p.status());
            assertTrue(p.items().stream().noneMatch(i->i.status().equals("CONDITION_MET")));
        }
    }
    @Test void unfinishedCandleChangesValuationButNotClosedCandleLevels() {
        var first=service(bars(100),true,"RAW","VND",now);var a=first.analyze("FPT",Mode.SWING);var expected=first.position("FPT",a.snapshotId(),position()).scenarios();
        var changed=new ArrayList<>(bars(100));changed.add(new Candle(now.getEpochSecond(),500,600,400,550,999999,false));
        var second=service(changed,true,"RAW","VND",now);var b=second.analyze("FPT",Mode.SWING);var result=second.position("FPT",b.snapshotId(),position());
        assertEquals(550,result.price());assertEquals(expected.items(),result.scenarios().items());assertEquals(expected.stopPrice(),result.scenarios().stopPrice());assertEquals(expected.asOf(),result.scenarios().asOf());
    }
    @Test void missingHistoryZeroAtrAdjustedAndUnknownUnitsDoNotFabricateLevels() {
        var flat=new ArrayList<Candle>();for(var c:bars(100))flat.add(new Candle(c.time(),100,100,100,100,1000,true));
        for(var source:List.of(bars(10),flat)) {
            var service=service(source,true,"RAW","VND",now);var s=service.analyze("FPT",Mode.SWING);assertNull(service.position("FPT",s.snapshotId(),position()).scenarios());
        }
        for(String basis:List.of("RAW","ADJUSTED")) {
            var service=service(bars(100),true,basis,basis.equals("RAW")?"SOURCE":"VND",now);var s=service.analyze("FPT",Mode.SWING);assertNull(service.position("FPT",s.snapshotId(),position()).scenarios());
        }
    }
    @Test void thousandVndAndVndProduceEquivalentLevelsAndInvalidInputsAreRejected() throws Exception {
        var small=bars(100);var scaled=small.stream().map(c->new Candle(c.time(),c.open()*1000,c.high()*1000,c.low()*1000,c.close()*1000,c.volume(),c.closed())).toList();
        var a=service(small,true,"RAW","THOUSAND_VND",now);var b=service(scaled,true,"RAW","VND",now);
        var pa=new PositionInput(null,110000.0,200.0,null,null,null,null);
        var x=a.analyze("FPT",Mode.SWING);var y=b.analyze("FPT",Mode.SWING);
        var first=a.position("FPT",x.snapshotId(),pa).scenarios();var second=b.position("FPT",y.snapshotId(),pa).scenarios();
        String report=System.getProperty("stock.scenario.report");
        if(report!=null)new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writerWithDefaultPrettyPrinter()
                .writeValue(java.nio.file.Path.of(report).toFile(),Map.of("snapshot",y,"result",b.position("FPT",y.snapshotId(),pa)));
        assertEquals(first.stopPrice(),second.stopPrice(),1e-7);for(int i=0;i<first.items().size();i++)assertEquals(first.items().get(i).triggerPrice(),second.items().get(i).triggerPrice(),1e-7);
        assertThrows(IllegalArgumentException.class,()->a.position("FPT",x.snapshotId(),new PositionInput(null,0.0,10.0,null,null,null,null)));
        assertThrows(IllegalArgumentException.class,()->a.position("FPT",x.snapshotId(),new PositionInput(null,100.0,1.5,null,null,null,null)));
    }
    @Test void closedBreakdownBlocksBothBuyAlternativesAndUsesTechnicalStopNotACostPercentage() {
        var source=new ArrayList<>(bars(100));var last=source.remove(source.size()-1);source.add(new Candle(last.time(),95,96,79,80,50000,true));
        var service=service(source,true,"RAW","VND",now);var s=service.analyze("FPT",Mode.SWING);var p=service.position("FPT",s.snapshotId(),position()).scenarios();
        assertEquals("RISK",p.status());assertTrue(p.items().stream().filter(i->i.side().equals("BUY")).noneMatch(i->i.status().equals("CONDITION_MET")));
        assertEquals("CONDITION_MET",p.items().stream().filter(i->i.id().equals("SELL_PROTECT")).findFirst().orElseThrow().status());
        assertNotEquals(110*.9,p.stopPrice(),1e-6);
    }
}
