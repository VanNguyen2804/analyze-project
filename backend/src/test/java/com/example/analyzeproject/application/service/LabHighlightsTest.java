package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.dto.AiLabReadView;
import com.example.analyzeproject.application.port.out.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.example.analyzeproject.application.service.ForecastProtocol.map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LabHighlightsTest {
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"),ZoneOffset.UTC);
    private Map<String,Object> row(String id,String model,String date) {
        return map("id",id,"category","MEGA","labModel",model,"slot",0,"targetDate",date,"createdAt","2026-11-07T09:00:00Z",
                "numbers",List.of(10,11,12,13,14,15),"modelVersion","test","codeVersion","test","configurationHash","test","configuration",Map.of());
    }
    @Test void highlightsShowOnlyCurrentPredictionsOfHistoricallySuccessfulModelsAcrossAllPages() {
        String target=ForecastProtocol.target("MEGA",clock.instant()).toString();
        var snapshots=new ArrayList<AiLabReadView.Snapshot>();var events=new ArrayList<Map<String,Object>>();
        for(int i=0;i<65;i++)snapshots.add(new AiLabReadView.Snapshot(row("filler"+i,"recency","2026-11-06"),true));
        snapshots.add(new AiLabReadView.Snapshot(row("past","logistic","2026-10-25"),true));
        events.add(map("forecastId","past","status","SETTLED","matched",3));
        snapshots.add(new AiLabReadView.Snapshot(row("new","logistic",target),true));
        snapshots.add(new AiLabReadView.Snapshot(row("not-successful","recency",target),true));
        snapshots.add(new AiLabReadView.Snapshot(row("invalid-current","logistic",target),false));
        snapshots.add(new AiLabReadView.Snapshot(row("invalid-past","shrunk_frequency","2026-10-25"),false));
        events.add(map("forecastId","invalid-past","status","SETTLED","matched",6));
        snapshots.add(new AiLabReadView.Snapshot(row("no-valid-evidence","shrunk_frequency",target),true));
        snapshots.add(new AiLabReadView.Snapshot(row("withdrawn-past","logistic_coverage","2026-10-25"),true));
        events.add(map("forecastId","withdrawn-past","status","RESULT_UNAVAILABLE","matched",6));
        snapshots.add(new AiLabReadView.Snapshot(row("withdrawn-model","logistic_coverage",target),true));
        var store=mock(AiLabStore.class);when(store.readOverview("MEGA")).thenReturn(new AiLabReadView(snapshots,events));
        var draws=mock(LotteryNumberRepository.class);var lab=new AiLabService(store,draws,true,com.example.analyzeproject.domain.service.AiLabModel.FeedbackPolicy.defaults(),clock);
        var result=lab.highlights("MEGA");assertEquals(1,result.size());assertEquals("new",result.get(0).get("id"));
        assertEquals(target,result.get(0).get("targetDate"));assertEquals(List.of(10,11,12,13,14,15),result.get(0).get("numbers"));
        assertEquals(3,((Map<?,?>)result.get(0).get("pastPerformance")).get("bestMatched"));
        assertEquals(result,lab.overview("MEGA",1).get("highlights"));
        verify(store,times(1)).readOverview("MEGA");verify(store).pageIds("MEGA",1);verifyNoMoreInteractions(store);verifyNoInteractions(draws);
        assertThrows(IllegalArgumentException.class,()->lab.highlights("OTHER"));
    }
    @Test void successfulHistoryWithoutACurrentSnapshotDoesNotRecycleOldNumbers() {
        var past=row("past","logistic","2026-10-25");var store=mock(AiLabStore.class);
        when(store.readOverview("MEGA")).thenReturn(new AiLabReadView(List.of(new AiLabReadView.Snapshot(past,true)),List.of(map("forecastId","past","status","SETTLED","matched",4))));
        var lab=new AiLabService(store,mock(LotteryNumberRepository.class),true,com.example.analyzeproject.domain.service.AiLabModel.FeedbackPolicy.defaults(),clock);
        assertTrue(lab.highlights("MEGA").isEmpty());
    }
    @Test void correctedOrWithdrawnHistoricalHitsRemoveCurrentPredictionsAfterRefresh() {
        String target=ForecastProtocol.target("MEGA",clock.instant()).toString();
        var events=new ArrayList<Map<String,Object>>();events.add(map("forecastId","past","status","SETTLED","matched",3));
        var rows=List.of(new AiLabReadView.Snapshot(row("past","logistic","2026-10-25"),true),new AiLabReadView.Snapshot(row("new","logistic",target),true));
        var store=mock(AiLabStore.class);when(store.readOverview("MEGA")).thenAnswer(call->new AiLabReadView(rows,events));
        var lab=new AiLabService(store,mock(LotteryNumberRepository.class),false,com.example.analyzeproject.domain.service.AiLabModel.FeedbackPolicy.defaults(),clock);
        assertEquals(1,lab.highlights("MEGA").size());
        events.add(map("forecastId","past","status","SETTLED","matched",2));lab.run();
        assertTrue(lab.highlights("MEGA").isEmpty());
        events.add(map("forecastId","past","status","SETTLED","matched",4));lab.run();
        assertEquals(1,lab.highlights("MEGA").size());
        events.add(map("forecastId","past","status","RESULT_UNAVAILABLE","matched",null));lab.run();
        assertTrue(lab.highlights("MEGA").isEmpty());
    }

    @Test void historicalHitBreakdownSeparatesVersionsAndConfigurationsAndSharesLabCohortKeys() {
        String target=ForecastProtocol.target("MEGA",clock.instant()).toString();
        var a=row("a","logistic","2026-10-25");a.put("modelVersion","v1");
        var b=row("b","logistic","2026-10-28");b.put("modelVersion","v2");
        var c=row("c","logistic","2026-10-28");c.put("modelVersion","v2");c.put("configurationHash","other");
        var snapshots=new ArrayList<AiLabReadView.Snapshot>();
        List.of(a,b,c,row("new","logistic",target)).forEach(f->snapshots.add(new AiLabReadView.Snapshot(f,true)));
        var events=List.of(map("forecastId","a","status","SETTLED","matched",3),map("forecastId","b","status","SETTLED","matched",4),map("forecastId","c","status","SETTLED","matched",3));
        var store=mock(AiLabStore.class);when(store.readOverview("MEGA")).thenReturn(new AiLabReadView(snapshots,events));
        var lab=new AiLabService(store,mock(LotteryNumberRepository.class),true,com.example.analyzeproject.domain.service.AiLabModel.FeedbackPolicy.defaults(),clock);
        var performance=(Map<?,?>)lab.highlights("MEGA").get(0).get("pastPerformance");
        assertEquals(3,performance.get("hitTickets"));assertEquals(2L,performance.get("hitDraws"));
        @SuppressWarnings("unchecked") var breakdown=(List<Map<String,Object>>)performance.get("cohorts");
        assertEquals(3,breakdown.size());breakdown.forEach(g->assertEquals(1,g.get("hitTickets")));
        @SuppressWarnings("unchecked") var overview=(List<Map<String,Object>>)lab.overview("MEGA",0).get("groups");
        for(var history:breakdown)assertTrue(overview.stream().anyMatch(g->g.get("cohort").equals(history.get("cohort"))&&g.get("cohortTag").equals(history.get("cohortTag"))));
    }

}
