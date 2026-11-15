package com.example.analyzeproject.application.service;

import java.util.*;
import java.time.*;
import com.example.analyzeproject.application.dto.AiLabReadView;
import com.example.analyzeproject.application.port.out.*;
import com.example.analyzeproject.domain.service.AiLabModel;
import org.junit.jupiter.api.Test;
import static com.example.analyzeproject.application.service.ForecastProtocol.map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiLabPredictionsTest {
    private final String target="2026-11-13";

    private List<Map<String,Object>> batch(String model,String date,String cohort,int... hits) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(int slot=0;slot<5;slot++) result.add(map("id",model+":"+date+":"+cohort+":"+slot,"model",model,"slot",slot,
                "cohort",cohort,"modelVersion",cohort,"targetDate",date,"historyHash",date,
                "numbers",List.of(slot+1,10,11,12,13,14),"settlement",hits.length==0?null:
                map("status","SETTLED","matched",hits[slot],"actualNumbers",List.of(1,2,3,4,5,6),"resultFingerprint",date)));
        return result;
    }
    private Map<?,?> performance(Map<String,Object> group) { return (Map<?,?>)group.get("performance"); }
    private Map<?,?> level(Map<?,?> stats,int threshold) {
        return ((List<?>)stats.get("levels")).stream().map(x->(Map<?,?>)x).filter(x->Objects.equals(threshold,x.get("threshold"))).findFirst().orElseThrow();
    }

    @Test void serviceUsesOnlyProspectiveSnapshotsWithoutGeneratingTicketsOrReadingDraws() {
        List<Map<String,Object>> rows=new ArrayList<>(batch("recency",target,"new"));
        rows.addAll(batch("recency","2026-11-11","old",3,2,1,0,0));
        var snapshots=new ArrayList<AiLabReadView.Snapshot>();
        var events=new ArrayList<Map<String,Object>>();
        for(var row:rows) {
            var forecast=new LinkedHashMap<>(row);forecast.put("labModel",row.get("model"));forecast.put("category","MEGA");forecast.put("createdAt","2026-11-10T10:00:00Z");
            snapshots.add(new AiLabReadView.Snapshot(forecast,true));
            if(row.get("settlement") instanceof Map<?,?> event) {
                var settlement=new LinkedHashMap<String,Object>();event.forEach((key,value)->settlement.put((String)key,value));
                settlement.put("forecastId",row.get("id"));events.add(settlement);
                settlement.put("frequencyMatched",0);settlement.put("randomMatched",0);
            }
        }
        var invalid=map("id","invalid","labModel","other","category","MEGA","targetDate",target,"createdAt","2026-11-10T10:00:00Z");
        snapshots.add(new AiLabReadView.Snapshot(invalid,false));
        var store=mock(AiLabStore.class);
        var draws=mock(LotteryNumberRepository.class);
        when(store.readOverview("MEGA")).thenReturn(new AiLabReadView(snapshots,events));
        var clock=Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"),ZoneOffset.UTC);
        var service=new AiLabService(store,draws,false,AiLabModel.FeedbackPolicy.defaults(),clock);
        var result=service.predictions("MEGA");
        assertEquals(target,result.get("targetDate"));
        var groups=(List<?>)result.get("groups");assertEquals(1,groups.size());
        assertEquals(1,((Map<?,?>)((Map<?,?>)groups.get(0)).get("performance")).get("evaluatedDraws"));
        verify(store).readOverview("MEGA");verifyNoMoreInteractions(store);verifyNoInteractions(draws);
    }

    @Test void ranksRepeatedHitsAheadOfOneOffPerfectRateAndCountsLosingDraws() {
        List<Map<String,Object>> rows=new ArrayList<>();
        rows.addAll(batch("many-hits","2026-11-01","v1",3,3,2,1,0));
        rows.addAll(batch("many-hits","2026-11-04","v2",0,0,0,0,0));
        rows.addAll(batch("many-hits","2026-11-06","v2",3,1,0,0,0));
        rows.addAll(batch("higher-rate","2026-11-11","v1",3,2,1,0,0));
        rows.addAll(batch("zero-rate","2026-11-11","v1",2,1,0,0,0));
        for(String model:List.of("many-hits","higher-rate","zero-rate","no-history"))rows.addAll(batch(model,target,"current"));
        var groups=AiLabPredictions.project(rows,target);
        assertEquals(List.of("many-hits","higher-rate","zero-rate","no-history"),groups.stream().map(g->g.get("model")).toList());
        var stats=performance(groups.get(0));
        assertEquals(3,stats.get("evaluatedDraws"));assertEquals(15,stats.get("evaluatedTickets"));
        assertEquals(3L,stats.get("hitTickets"));assertEquals(2L,level(stats,3).get("drawHits"));
        assertEquals(200.0/3,(Double)level(stats,3).get("drawPercent"),1e-9);
        assertEquals(2L,level(stats,2).get("drawHits"));assertEquals(2L,level(stats,1).get("drawHits"));
        assertEquals(0.0,level(performance(groups.get(2)),3).get("drawPercent"));
        assertNull(level(performance(groups.get(3)),3).get("drawPercent"));
        assertEquals(2,((List<?>)groups.get(0).get("history")).size());
    }

    @Test void balancesRateAndSampleSizeWithinRepeatedHitsAndIsOrderIndependent() {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(String model:List.of("consistent","sparse","one-off","unmeasured"))rows.addAll(batch(model,target,"current"));
        for(int i=0;i<10;i++) {
            String date=LocalDate.of(2026,10,1).plusDays(i).toString();
            rows.addAll(batch("sparse",date,"v1",i<2?3:0,0,0,0,0));
            if(i<3)rows.addAll(batch("consistent",date,"v1",i<2?3:0,0,0,0,0));
        }
        rows.addAll(batch("one-off","2026-11-01","v1",3,3,3,3,3));
        var expected=List.of("consistent","sparse","one-off","unmeasured");
        assertEquals(expected,AiLabPredictions.project(rows,target).stream().map(g->g.get("model")).toList());
        Collections.reverse(rows);
        assertEquals(expected,AiLabPredictions.project(rows,target).stream().map(g->g.get("model")).toList());
    }
    @Test void ranksIndividualSlotsByTheirOwnHistoryAndReturnsActualResults() {
        List<Map<String,Object>> rows=new ArrayList<>(batch("model",target,"v2"));
        rows.addAll(batch("model","2026-11-01","v2",3,0,0,0,0));
        rows.addAll(batch("model","2026-11-04","v2",0,3,0,0,0));
        rows.addAll(batch("model","2026-11-06","v2",0,4,0,0,0));
        rows.addAll(batch("model","2026-10-30","incompatible",6,6,6,6,6));
        var group=AiLabPredictions.project(rows,target).get(0);
        var priorities=(List<Map<String,Object>>)group.get("ticketPriorities");
        assertEquals(5,priorities.size());
        var first=priorities.get(0);
        assertEquals(1,first.get("slot"));assertEquals(1,first.get("rank"));
        assertEquals(2L,level(performance(first),3).get("drawHits"));
        assertEquals(3,performance(first).get("evaluatedDraws"));
        assertEquals(1L,level(performance(priorities.get(1)),3).get("drawHits"));
        var timeline=(List<Map<String,Object>>)first.get("timeline");
        assertEquals(3,timeline.size());assertEquals("2026-11-06",timeline.get(0).get("targetDate"));
        assertEquals(4,timeline.get(0).get("matched"));
        assertEquals(List.of(1,2,3,4,5,6),timeline.get(0).get("actualNumbers"));
        assertEquals(List.of(2,10,11,12,13,14),timeline.get(0).get("numbers"));
        assertEquals("v2",timeline.get(0).get("modelVersion"));
    }

    @Test void excludesIncompleteAmbiguousWithdrawnAndInconsistentBatches() {
        List<Map<String,Object>> rows=new ArrayList<>(batch("model",target,"new"));
        rows.addAll(batch("model","2026-11-01","v1",3,2,1,0,0).subList(0,4));
        rows.addAll(batch("model","2026-11-04","v1",3,2,1,0,0));
        rows.addAll(batch("model","2026-11-04","duplicate",3,2,1,0,0));
        var withdrawn=batch("model","2026-11-06","v1",3,2,1,0,0);
        withdrawn.get(0).put("settlement",map("status","RESULT_UNAVAILABLE","matched",3));rows.addAll(withdrawn);
        var mixed=batch("model","2026-11-08","v1",3,2,1,0,0);
        mixed.get(0).put("settlement",map("status","SETTLED","matched",3,"actualNumbers",List.of(7,8,9,10,11,12),"resultFingerprint","other"));rows.addAll(mixed);
        rows.addAll(batch("model","2026-11-15","v1",6,6,6,6,6));
        var groups=AiLabPredictions.project(rows,target);
        assertEquals(1,groups.size());assertEquals(0,performance(groups.get(0)).get("evaluatedDraws"));
        rows.remove(0);assertTrue(AiLabPredictions.project(rows,target).isEmpty());
    }

    @Test void correctionsChangeRatesAndThresholdsAreCumulativePerDraw() {
        List<Map<String,Object>> rows=new ArrayList<>(batch("model",target,"new"));
        var past=batch("model","2026-11-11","old",4,3,2,1,0);rows.addAll(past);
        var before=performance(AiLabPredictions.project(rows,target).get(0));
        for(int threshold:List.of(3,2,1))assertEquals(100.0,level(before,threshold).get("drawPercent"));
        assertEquals(2L,before.get("hitTickets"));
        for(var row:past)row.put("settlement",map("status","SETTLED","matched",0,"actualNumbers",List.of(1,2,3,4,5,6),"resultFingerprint","corrected"));
        var after=performance(AiLabPredictions.project(rows,target).get(0));
        assertEquals(1,after.get("evaluatedDraws"));assertEquals(0.0,level(after,3).get("drawPercent"));assertNull(after.get("latestHitDate"));
    }
}
