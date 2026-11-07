package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.*;
import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.domain.service.*;
import com.example.analyzeproject.presentation.rest.PredictionResearchController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PredictionResearchTest {
    private List<LotteryNumber> history(String category, int count) {
        var rows = new ArrayList<LotteryNumber>();
        LocalDate date = category.equals("MEGA") ? LocalDate.of(2026,8,2) : LocalDate.of(2026,8,1);
        int max = category.equals("MEGA") ? 45 : 55;
        for (int i = 0; i < count; i++) {
            int offset = i * 7;
            var numbers = IntStream.range(0,6).map(n -> 1 + (offset + n) % max).boxed().toList();
            rows.add(TestDraws.verified(new LotteryNumber(date,category,numbers,null,null)));
            date = TemporalRelationshipModel.scheduledAfter(date,category);
        }
        return rows;
    }
    @Test void shrinkagePreservesSixExpectedNumbersAndHandlesEmptyOrQuarantinedHistory() {
        for (String category : List.of("MEGA","POWER")) {
            var rows = history(category,15); var p = AiLabModel.shrunkFrequency(rows,category,16);
            assertEquals(6, Arrays.stream(p).sum(),1e-12);
            Collections.reverse(rows); assertArrayEquals(p,AiLabModel.shrunkFrequency(rows,category,16));
            var uniform = AiLabModel.shrunkFrequency(List.of(),category,16);
            assertEquals(6.0/(uniform.length-1),uniform[1],1e-12);
            var latest=rows.get(0); var revision=new LotteryNumber(latest.getDrawDate(),category,List.of(1,2,3,4,5,6),null,null);
            revision.setQuality(Map.of("status","QUARANTINED"));revision.setCreatedAt(latest.getCreatedAt().plusDays(1));rows.add(revision);
            assertEquals(14,DrawQuality.history(rows).size());
        }
        assertThrows(IllegalArgumentException.class,()->AiLabModel.shrunkFrequency(List.of(),"OTHER",16));
        assertThrows(IllegalArgumentException.class,()->AiLabModel.shrunkFrequency(List.of(),"MEGA",Double.NaN));
        assertThrows(IllegalArgumentException.class,()->new AiLabModel.TrainingPolicy(80,.2,Double.NaN,15,0));
    }
    @Test void coverageIsDeterministicAndMeasuresSubsetsWithoutChangingTheAnchorOrBudget() {
        for (int max : List.of(45,55)) {
            var ranking=IntStream.rangeClosed(1,max).boxed().toList();
            for (int budget : List.of(1,3,5)) {
                var selected=CoveragePortfolio.select(ranking,max,budget,"frozen-seed",CoveragePortfolio.Policy.defaults());
                assertEquals(budget,new HashSet<>(selected).size());
                assertEquals(List.of(1,2,3,4,5,6),selected.get(0));
                assertEquals(selected,CoveragePortfolio.select(ranking,max,budget,"frozen-seed",CoveragePortfolio.Policy.defaults()));
                selected.forEach(t->{assertEquals(6,new HashSet<>(t).size());assertTrue(t.stream().allMatch(n->n>=1&&n<=max));});
                var diagnostics=CoveragePortfolio.diagnostics(selected);
                assertTrue(((Integer)diagnostics.get("coveredQuadruples"))<=15*budget);
                assertTrue(((Integer)diagnostics.get("coveredTriples"))<=20*budget);
            }
        }
    }
    @Test @SuppressWarnings("unchecked") void replayUsesFrozenEarlierSelectionAndCannotSeeLaterOutcomes() {
        var study=new PredictionBenchmark(null); var options=new PredictionBenchmark.Options(10,3,180,3);
        var rows=history("MEGA",18);var before=study.run(rows,"MEGA",options);
        assertEquals("COMPLETED",before.get("status"));
        Collections.reverse(rows); assertEquals(ForecastProtocol.hash(before),ForecastProtocol.hash(study.run(rows,"MEGA",options)));
        Collections.reverse(rows);
        var observations=(List<Map<String,Object>>)before.get("byDraw");
        assertEquals(3,observations.size());
        observations.forEach(r->assertTrue(((String)r.get("trainingThrough")).compareTo((String)r.get("targetDate"))<0));
        var last=rows.get(rows.size()-1);last.setNumbers(List.of(35,36,37,38,39,40));TestDraws.verified(last);
        var after=(List<Map<String,Object>>)study.run(rows,"MEGA",options).get("byDraw");
        assertEquals(ForecastProtocol.hash(observations.subList(0,2)),ForecastProtocol.hash(after.subList(0,2)));
        assertEquals("INSUFFICIENT_DATA",study.run(List.of(),"MEGA",options).get("status"));
        var withoutPrevious=new ArrayList<>(rows);withoutPrevious.remove(withoutPrevious.size()-2);
        var report=study.run(withoutPrevious,"MEGA",options);
        assertTrue(((List<?>)((Map<?,?>)report.get("manifest")).get("excluded")).size()>0);
    }
    @Test void zeroEventIntervalIsNotZeroCertainty() {
        var interval=PredictionBenchmark.wilson(0,30);
        assertEquals(0,interval.get(0),1e-12);assertTrue(interval.get(1)>0);
        assertTrue(PredictionBenchmark.wilson(0,0).isEmpty());
    }
    @Test void inventoryOnlyReadsAndQuarantinedLatestRevisionDoesNotFallBack() {
        var repository=mock(LotteryNumberRepository.class);var forecasts=mock(ForecastStore.class);var lab=mock(AiLabStore.class);
        var rows=history("MEGA",15);var latest=rows.get(rows.size()-1);
        var clock=Clock.fixed(latest.getDrawDate().atTime(20,0).atZone(ForecastProtocol.ZONE).toInstant(),ZoneOffset.UTC);
        when(repository.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(rows);
        when(forecasts.readLedger()).thenReturn(Map.of());when(lab.ledger()).thenReturn(Map.of());
        var service=new PredictionResearchService(repository,forecasts,lab,clock,null);
        assertEquals(true,service.inventory("MEGA").get("fresh"));
        var bad=new LotteryNumber(latest.getDrawDate(),"MEGA",List.of(1,2,3,4,5,6),null,null);
        bad.setCreatedAt(latest.getCreatedAt().plusDays(1));bad.setQuality(Map.of("status","QUARANTINED"));rows.add(bad);
        var inventory=service.inventory("MEGA"); assertEquals(false,inventory.get("fresh"));assertEquals(14,inventory.get("eligibleDraws"));
        verify(forecasts,never()).capture(any());verify(forecasts,never()).reconcile(any(),any());
        verify(lab,never()).captureBatch(any());verify(lab,never()).reconcile(any(),any());
    }
    @Test void httpValidatesOptionsAndDoesNotRunBenchmarkOnGet() throws Exception {
        var research=mock(PredictionResearchService.class);
        when(research.inventory("MEGA")).thenReturn(Map.of("eligibleDraws",39));
        var mvc=MockMvcBuilders.standaloneSetup(new PredictionResearchController(research)).build();
        mvc.perform(get("/api/prediction-research/inventory?category=mega")).andExpect(status().isOk()).andExpect(jsonPath("$.eligibleDraws").value(39));
        verify(research,never()).benchmark(any(),any());
        mvc.perform(post("/api/prediction-research/benchmark").contentType("application/json").content("{\"warmupDraws\":0,\"maximumTargets\":500,\"trainingWindow\":0,\"budget\":0}")).andExpect(status().isBadRequest());
    }
}
