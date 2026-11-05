package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.example.analyzeproject.domain.service.stock.StockPositionPlanEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockPositionPlanTest {
    final Instant now=Instant.parse("2026-11-03T09:00:00Z"),start=now.minusSeconds(86400*5);
    PositionPolicy policy(double risk,List<Double> budgets) {
        return new PositionPolicy(10,risk,27000000,57000000,budgets,
                List.of(new ProfitTarget(10.0,null,30),new ProfitTarget(20.0,null,30)),5.0);
    }
    PositionInput input(PositionPolicy policy,List<PositionExecution> executions,Double available,Double peak) {
        return new PositionInput(null,30000.0,1000.0,null,null,100.0/6,1000.0,new PositionPlanInput(start,policy,executions,peak,available));
    }
    Snapshot snapshot(double price,boolean verified,String basis,String unit,boolean open) {
        double factor="THOUSAND_VND".equals(unit)?1000:1;
        List<Candle> bars=new ArrayList<>();
        for(int i=0;i<3;i++){double close=(price-200*(2-i))/factor;bars.add(new Candle(now.minusSeconds((3-i)*86400).getEpochSecond(),close,close+100/factor,close-100/factor,close,10000,true));}
        if(open)bars.add(new Candle(now.getEpochSecond(),price/factor,price/factor,price/factor,price/factor,1000,false));
        var feed=new Feed(bars,now,"TEST ONLY",unit,basis,verified,List.of());
        var rec=new Recommendation("test","WATCH","Test","Test",unit,bars.get(2).time(),price/factor,32000/factor,24000/factor,25000/factor,36000/factor,1.5,1.2,List.of(),List.of(),List.of());
        var analysis=new Analysis("WATCH",List.of(),new Indicator(28000/factor,29000/factor,48,400/factor,27000/factor,32000/factor),null,rec);
        return new Snapshot("FPT",Mode.SWING,"1D","test-plan",feed,feed,"RECENT","TEST",bars.get(2).time(),analysis,null);
    }
    PositionPlan run(Snapshot s,PositionInput input) {return StockPositionPlanEngine.evaluate(s,input,"RECENT",now);}
    PlanStep step(PositionPlan plan,String id){return plan.steps().stream().filter(s->id.equals(s.id())).findFirst().orElseThrow();}

    @Test void averagingKeepsOriginalStopAndDoesNotEraseLossOrInventExecutions() {
        var input=input(policy(6000000,List.of(13500000.0,0.0)),List.of(),null,null);
        var plan=run(snapshot(27000,true,"RAW","VND",false),input);
        var buy=step(plan,"AVG1");assertEquals(500,buy.quantity());assertEquals(29000,buy.averageAfter(),1e-8);
        assertEquals(-3000000,buy.pnlAtReference(),1e-7);assertEquals(6000000,buy.lossAtStop(),1e-7);
        assertEquals(25000,plan.originalStop(),1e-8);assertEquals(1000,plan.quantity());assertEquals(0,plan.additionalSpent());assertEquals("READY",buy.status());
        var recorded=input(input.plan().policy(),List.of(new PositionExecution("AVG1",27000,500,now.minusSeconds(600),null)),1000.0,null);
        var after=run(snapshot(27000,true,"RAW","VND",false),recorded);
        assertEquals(1500,after.quantity());assertEquals(29000,after.averageCost());assertEquals(1000,after.sellableQuantity());
        assertEquals(plan.originalStop(),after.originalStop());assertEquals("DONE",step(after,"AVG1").status());
        assertEquals(after,run(snapshot(27000,true,"RAW","VND",false),recorded));
    }
    @Test void bothLossThresholdsAndCashRiskOverrideBuyPlans() {
        var p=input(policy(6000000,List.of(13500000.0,13500000.0)),List.of(),null,null);
        var breached=run(snapshot(25000,true,"RAW","VND",false),p);
        assertEquals("RISK",breached.status());assertEquals("BLOCKED",step(breached,"AVG1").status());assertEquals("READY",step(breached,"REDUCE").status());
        var noRoom=run(snapshot(27000,true,"RAW","VND",false),input(policy(5000000,List.of(13500000.0,0.0)),List.of(),null,null));
        assertEquals(0,step(noRoom,"AVG1").quantity());
        var lowBudget=run(snapshot(27000,true,"RAW","VND",false),input(policy(8000000,List.of(100.0,0.0)),List.of(),null,null));
        assertEquals("BLOCKED",step(lowBudget,"AVG1").status());
        var limited=run(snapshot(27000,true,"RAW","VND",false),input(policy(6500000,List.of(13500000.0,13500000.0)),List.of(),null,null));
        assertEquals("WAIT_PREVIOUS",step(limited,"AVG2").status());
        assertTrue(step(limited,"AVG2").lossAtStop()<=6500000+.001);
    }
    @Test void profitGapsCannotOversellAndRecordedStepsDoNotRepeat() {
        var p=input(policy(8000000,List.of(0.0,0.0)),List.of(),400.0,null);
        var plan=run(snapshot(38000,true,"RAW","VND",false),p);
        assertEquals(300,step(plan,"TP1").quantity());assertEquals(100,step(plan,"TP2").quantity());
        assertEquals("WAIT_PREVIOUS",step(plan,"TP2").status());assertEquals(1000,plan.quantity());
        var done=new PositionExecution("TP1",37000,300,now.minusSeconds(2*86400),400.0);
        var after=run(snapshot(38000,true,"RAW","VND",false),input(p.plan().policy(),List.of(done),100.0,null));
        assertEquals(700,after.quantity());assertEquals(2100000,after.realizedPnl());assertEquals("DONE",step(after,"TP1").status());
        assertEquals("READY",step(after,"TP2").status());assertTrue(after.effectiveStop()>after.originalStop());
        var falling=run(snapshot(36000,true,"RAW","VND",false),input(p.plan().policy(),List.of(done),100.0,after.observedPeak()));
        assertTrue(falling.effectiveStop()>=after.effectiveStop());
        assertThrows(IllegalArgumentException.class,()->run(snapshot(38000,true,"RAW","VND",false),input(p.plan().policy(),List.of(done,done),null,null)));
        assertThrows(IllegalArgumentException.class,()->run(snapshot(38000,true,"RAW","VND",false),input(p.plan().policy(),List.of(new PositionExecution("TP1",37000,500,now,400.0)),null,null)));
    }
    @Test void qualityGatesCurrencyAndClosedDecisionAreIndependentFromValuation() {
        var p=input(policy(8000000,List.of(13500000.0,0.0)),List.of(),null,null);
        var vnd=run(snapshot(27000,true,"RAW","VND",false),p),thousand=run(snapshot(27000,true,"RAW","THOUSAND_VND",false),p);
        assertEquals(vnd,thousand);
        for(var s:List.of(snapshot(25000,false,"RAW","VND",false),snapshot(25000,true,"UNKNOWN","VND",false))) {
            var plan=run(s,p);assertEquals("REFERENCE",plan.status());assertFalse(plan.actionable());assertNotEquals("READY",step(plan,"REDUCE").status());
        }
        assertEquals("REFERENCE",StockPositionPlanEngine.evaluate(snapshot(25000,true,"RAW","VND",false),p,"STALE",now).status());
        var s=snapshot(27000,true,"RAW","VND",true);var bars=new ArrayList<>(s.data().candles());bars.set(3,new Candle(now.getEpochSecond(),24000,24000,24000,24000,100,false));
        var feed=new Feed(bars,now,"TEST","VND","RAW",true,List.of());
        var live=new Snapshot(s.symbol(),s.mode(),s.interval(),s.snapshotId(),feed,feed,s.freshness(),s.marketStatus(),s.lastClosedBarAt(),s.analysis(),null);
        var plan=run(live,p);assertEquals(24000,plan.valuationPrice());assertEquals(27000,plan.decisionPrice());assertNotEquals("RISK",plan.status());
        assertThrows(IllegalArgumentException.class,()->run(snapshot(27000,true,"ADJUSTED","VND",false),p));
    }
    @Test void unknownOrZeroSellableBlocksExecutionSizedRecommendations() {
        var policy=policy(8000000,List.of());var base=input(policy,List.of(),null,null);
        var p=new PositionInput(null,30000.0,1000.0,null,null,100.0/6,null,base.plan());
        assertEquals("NEED_SELLABLE",step(run(snapshot(37000,true,"RAW","VND",false),p),"TP1").status());
        assertEquals(0,step(run(snapshot(37000,true,"RAW","VND",false),input(policy,List.of(),0.0,null)),"TP1").quantity());
        assertThrows(IllegalArgumentException.class,()->run(snapshot(37000,true,"RAW","VND",false),input(policy,List.of(),1001.0,null)));
    }
    @Test void exportsClearlyLabeledSyntheticPreviewForLocalVisualVerification() throws Exception {
        var s=snapshot(27000,true,"RAW","VND",false);var plan=run(s,input(policy(6000000,List.of(13500000.0,0.0)),List.of(),null,null));
        var path=java.nio.file.Path.of("target","stock-plan-preview.json");
        new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writerWithDefaultPrettyPrinter().writeValue(path.toFile(),plan);
    }
}
