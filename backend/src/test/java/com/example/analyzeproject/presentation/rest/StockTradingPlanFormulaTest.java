package com.example.analyzeproject.presentation.rest;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class StockTradingPlanFormulaTest {
 private final com.example.analyzeproject.application.service.StockTradingPlanService service = new com.example.analyzeproject.application.service.StockTradingPlanService(null,new com.example.analyzeproject.application.service.StockTradingPolicy(20,120,15,30,60,0.25,0.5,1,1.5,1,2,0.25,1.5,0.25,1.5,2,2,3,4));
 @Test void targetsFollowResistanceAndLongPlanHasWiderProtection(){
  List<Candle> bars=new ArrayList<>();for(int i=0;i<30;i++)bars.add(new Candle(1700000000L+i*86400L,100,110,90,100,1000,true));
  var input=new PositionInput(null,100.0,10.0,null,null,null,null);
  var swing=service.plan(bars,20,1,input,"SWING","Swing","");
  var longer=service.plan(bars,30,1,input,"LONG","Long","");
  assertEquals(20.0,(Double)swing.get("atr14"),1e-9);
  assertEquals(105.0,(Double)swing.get("target1"),1e-9);
  assertEquals(130.0,(Double)swing.get("target2"),1e-9);
  assertEquals(70.0,(Double)swing.get("stop"),1e-9);
  assertEquals(60.0,(Double)longer.get("stop"),1e-9);
  assertEquals(150.0,(Double)longer.get("target2"),1e-9);
  assertEquals(0.4,(Double)swing.get("rewardRisk"),1e-9);
 }
 @Test void ema200SeedsAtAverageAndRecalculatesTargets(){
  List<Candle> bars=new ArrayList<>();for(int i=0;i<200;i++)bars.add(new Candle(1700000000L+i*86400L,100,110,90,100,1000,true));
  var input=new PositionInput(null,100.0,10.0,null,null,null,null);
  var base=service.plan(bars,20,1,input,"SWING","Swing","");
  var result=service.emaPlan(base,bars,1,input);
  assertEquals(100.0,(Double)result.get("ema200"),1e-9);
  assertEquals(95.0,(Double)result.get("entryLow"),1e-9);
  assertEquals(105.0,(Double)result.get("entryHigh"),1e-9);
  assertEquals(70.0,(Double)result.get("stop"),1e-9);
  assertEquals(175.0,(Double)result.get("target1"),1e-9);
 }
 @Test void changingPolicyChangesComputedEntryWithoutChangingCandles(){
  var policy = new com.example.analyzeproject.application.service.StockTradingPolicy(20,120,15,30,60,.75,.5,1,1.5,1,2,.25,1.5,.25,1.5,2,2,3,4);
  var changed = new com.example.analyzeproject.application.service.StockTradingPlanService(null,policy);
  List<Candle> bars=new ArrayList<>();for(int i=0;i<30;i++)bars.add(new Candle(1700000000L+i*86400L,100,110,90,100,1000,true));
  var input=new PositionInput(null,100.0,10.0,null,null,null,null);
  var original=service.plan(bars,20,1,input,"SWING","Swing","");
  var revised=changed.plan(bars,20,1,input,"SWING","Swing","");
  assertEquals(95.0,(Double)original.get("entryHigh"),1e-9);
  assertEquals(105.0,(Double)revised.get("entryHigh"),1e-9);
  assertEquals(original.get("support"),revised.get("support"));
 }
 @Test void inconsistentPolicyIsRejected(){
  assertThrows(IllegalArgumentException.class,()->new com.example.analyzeproject.application.service.StockTradingPolicy(20,120,1,30,60,.25,.5,1,1.5,1,2,.25,1.5,.25,1.5,2,2,3,4));
 }
}