package com.example.analyzeproject.application.service;
import com.example.analyzeproject.domain.service.*;

import com.example.analyzeproject.domain.model.LotteryNumber;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.example.analyzeproject.application.port.out.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiLabTest {
    @Test void novemberEleventhAddsFeedbackCoverageWithoutReplacingSavedModels() {
        var now=Instant.parse("2026-11-10T13:00:00Z");
        var store=mock(AiLabStore.class);var repository=mock(LotteryNumberRepository.class);
        List<LotteryNumber> history=new ArrayList<>();
        LocalDate date=LocalDate.of(2026,11,8);
        while(history.size()<12) {
            if(Set.of(3,5,7).contains(date.getDayOfWeek().getValue()))
                history.add(TestDraws.verified(new LotteryNumber(date,"MEGA",List.of(1,2,3,4,5,6),null,null)));
            date=date.minusDays(1);
        }
        when(repository.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(history);
        Set<String> ids=new HashSet<>();
        for(String model:AiLabModel.MODELS) if(!model.equals("logistic_feedback_coverage"))
            for(int slot=0;slot<5;slot++) ids.add("MEGA:2026-11-11:lab-"+model+"-"+slot);
        when(store.snapshotIds()).thenReturn(ids);
        when(store.feedbackSnapshots(anyString(),any(),anyInt(),any())).thenReturn(List.of());
        List<Map<String,Object>> saved=new ArrayList<>();
        doAnswer(call->{List<Map<String,Object>> batch=call.getArgument(0);saved.addAll(batch);batch.forEach(f->ids.add((String)f.get("id")));return null;}).when(store).captureBatch(anyList());
        var lab=new AiLabService(store,repository,true,AiLabModel.FeedbackPolicy.defaults(),Clock.fixed(now,ZoneOffset.UTC));
        lab.run();assertEquals(5,saved.size());
        assertEquals(5,saved.stream().map(f->f.get("numbers")).distinct().count());
        for(var f:saved) {
            assertEquals("2026-11-11",f.get("targetDate"));
            assertEquals("logistic_feedback_coverage",f.get("labModel"));
            assertTrue(ForecastProtocol.isProspective(f));
            var explanation=(Map<?,?>)f.get("explanation");
            assertEquals(CoveragePortfolio.VERSION,explanation.get("selectionPolicy"));
            assertEquals(0,((Map<?,?>)explanation.get("feedback")).get("draws"));
        }
        lab.run();assertEquals(5,saved.size());verify(store,times(1)).captureBatch(anyList());
    }

    @Test void feedbackComparisonUsesOnlyCompletePairedDrawsWithSameHistory() {
        List<Map<String,Object>> snapshots=new ArrayList<>(), events=new ArrayList<>();
        var actual=TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,25),"MEGA",List.of(1,2,3,30,31,32),null,null));
        for (String model:List.of("logistic","logistic_no_feedback")) for(int slot=0;slot<5;slot++) {
            var f=ForecastProtocol.prepare(history(),"MEGA","lab-"+model+"-"+slot,AiLabModel.VERSION,"code",
                    Map.of("budget",5,"feedbackPolicy",model),List.of(1,2,3,4,5,6+slot),before);
            f.put("labModel",model); f.put("slot",slot); snapshots.add(f); events.add(ForecastProtocol.settle(f,List.of(actual),after,null));
        }
        var report=AiLabService.evaluate(snapshots,events).stream().filter(g->"logistic".equals(g.get("model"))).findFirst().orElseThrow();
        var comparison=(Map<?,?>)report.get("feedbackComparison");
        assertEquals(1,comparison.get("pairedDraws")); assertEquals(0.0,comparison.get("difference"));
        snapshots.get(5).put("historyHash","different");
        report=AiLabService.evaluate(snapshots,events).stream().filter(g->"logistic".equals(g.get("model"))).findFirst().orElseThrow();
        assertEquals(0,((Map<?,?>)report.get("feedbackComparison")).get("pairedDraws"));
    }
    @Test void coverageFeedbackComparisonUsesTheCoverageControl() {
        List<Map<String,Object>> snapshots=new ArrayList<>(), events=new ArrayList<>();
        var actual=TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,25),"MEGA",List.of(1,2,3,30,31,32),null,null));
        for (String model:List.of("logistic_feedback_coverage","logistic_coverage")) for(int slot=0;slot<5;slot++) {
            var f=ForecastProtocol.prepare(history(),"MEGA","lab-"+model+"-"+slot,AiLabModel.VERSION,"code",
                    Map.of("budget",5,"feedbackPolicy",model),List.of(1,2,3,4,5,6+slot),before);
            f.put("labModel",model); f.put("slot",slot); snapshots.add(f); events.add(ForecastProtocol.settle(f,List.of(actual),after,null));
        }
        var report=AiLabService.evaluate(snapshots,events).stream().filter(g->"logistic_feedback_coverage".equals(g.get("model"))).findFirst().orElseThrow();
        var comparison=(Map<?,?>)report.get("feedbackComparison");
        assertEquals(1,comparison.get("pairedDraws")); assertEquals(0.0,comparison.get("difference"));
        snapshots.get(5).put("historyHash","different");
        report=AiLabService.evaluate(snapshots,events).stream().filter(g->"logistic_feedback_coverage".equals(g.get("model"))).findFirst().orElseThrow();
        assertEquals(0,((Map<?,?>)report.get("feedbackComparison")).get("pairedDraws"));
    }
    private final Instant before=Instant.parse("2026-10-25T10:59:59Z"), after=Instant.parse("2026-10-25T12:00:00Z");
    private List<LotteryNumber> history() {
        List<LotteryNumber> rows=new ArrayList<>(); LocalDate date=LocalDate.of(2026,9,30);
        while(date.isBefore(LocalDate.of(2026,10,25))) {
            rows.add(TestDraws.verified(new LotteryNumber(date,"MEGA",List.of(1,2,3,4,5,6),null,null)));
            date=TemporalRelationshipModel.scheduledAfter(date,"MEGA");
        }
        return rows;
    }
    @Test void learningAndTicketsAreDeterministicAndReactToHistory() {
        var rows=history(); var scores=AiLabModel.scores(rows,"MEGA","logistic");
        assertTrue(scores[1]>scores[30]);
        var tickets=AiLabModel.tickets(scores,"seed");
        assertEquals(5,new HashSet<>(tickets).size());
        tickets.forEach(t->{assertEquals(6,new HashSet<>(t).size()); assertTrue(t.stream().allMatch(n->n>=1&&n<=45));});
        Collections.reverse(rows);
        assertArrayEquals(scores,AiLabModel.scores(rows,"MEGA","logistic"));
        assertEquals(tickets,AiLabModel.tickets(scores,"seed"));
        var changed=history(); changed.forEach(r->{r.setNumbers(List.of(30,31,32,33,34,35));TestDraws.verified(r);});
        assertTrue(AiLabModel.scores(changed,"MEGA","logistic")[30]>AiLabModel.scores(changed,"MEGA","logistic")[1]);
        assertEquals(5,AiLabModel.tickets(AiLabModel.scores(rows,"POWER","random"),"power").size());
    }
    @Test void measuresCompleteDrawsAndLatestResultRatherThanCountingTicketsAsDraws() {
        List<Map<String,Object>> snapshots=new ArrayList<>(), events=new ArrayList<>();
        var actual=TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,25),"MEGA",List.of(1,2,3,30,31,32),null,null));
        for(int i=0;i<5;i++) {
            var f=ForecastProtocol.prepare(history(),"MEGA","lab-logistic-"+i,AiLabModel.VERSION,"code",Map.of("budget",5),List.of(1,2,3,4,5,6+i),before);
            f.put("labModel","logistic"); f.put("slot",i); snapshots.add(f); events.add(ForecastProtocol.settle(f,List.of(actual),after,null));
        }
        var report=AiLabService.evaluate(snapshots,events).get(0);
        assertEquals(1,report.get("evaluatedDraws")); assertEquals(100.0,report.get("ticketHit3Percent")); assertEquals(100.0,report.get("drawHit3Percent"));
        var progress=(AiLabProgress.Report)report.get("progress");assertEquals(15,progress.totalMatched());assertEquals(5,progress.evaluatedTickets());
        assertEquals("COLLECTING",report.get("status")); assertFalse((Boolean)report.get("continuous60"));
        events.add(ForecastProtocol.settle(snapshots.get(0),List.of(),after,events.get(0)));
        assertEquals(0,AiLabService.evaluate(snapshots,events).get(0).get("evaluatedDraws"));
        assertEquals(0,((AiLabProgress.Report)AiLabService.evaluate(snapshots,events).get(0).get("progress")).totalMatched());
        snapshots.get(0).put("createdAt","2026-10-25T11:00:00Z");
        assertEquals(0,AiLabService.evaluate(snapshots,events).get(0).get("evaluatedDraws"));
    }
    @Test void versionsAreSeparateAndEmptyEvidenceNeverPasses() {
        assertTrue(AiLabService.evaluate(List.of(),List.of()).isEmpty());
        var a=ForecastProtocol.prepare(history(),"MEGA","a","v1","code",Map.of(),List.of(1,2,3,4,5,6),before); a.put("labModel","logistic");
        var b=new LinkedHashMap<>(a); b.put("codeVersion","other");
        var groups=AiLabService.evaluate(List.of(a,b),List.of()); assertEquals(2,groups.size());
        groups.forEach(g->{assertEquals("COLLECTING",g.get("status")); assertNull(g.get("drawHit3Percent"));});
    }
    @Test void capturesCompleteBatchesOnceWithEqualBudgetControlsAndNoFutureData() {
        var store=mock(AiLabStore.class); var repository=mock(LotteryNumberRepository.class);
        List<LotteryNumber> rows=new ArrayList<>(); var now=Instant.now(); LocalDate date=ForecastProtocol.expected("MEGA",now);
        for(int i=0;i<12;i++) {
            rows.add(TestDraws.verified(new LotteryNumber(date,"MEGA",List.of(1,2,3,4,5,6),null,null)));
            do {date=date.minusDays(1);} while(!Set.of(3,5,7).contains(date.getDayOfWeek().getValue()));
        }
        rows.add(TestDraws.verified(new LotteryNumber(ForecastProtocol.target("MEGA",now),"MEGA",List.of(30,31,32,33,34,35),null,null)));
        when(repository.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(rows);
        List<Map<String,Object>> saved=new ArrayList<>(); when(store.snapshots()).thenAnswer(call->new ArrayList<>(saved));
        when(store.snapshotIds()).thenAnswer(call->saved.stream().map(f->(String)f.get("id")).collect(java.util.stream.Collectors.toSet()));
        when(store.feedbackSnapshots(anyString(),any(),anyInt(),any())).thenAnswer(call->new ArrayList<>(saved));
        doAnswer(call->{ List<Map<String,Object>> batch=call.getArgument(0); assertEquals(5,batch.size()); saved.addAll(batch); return null; }).when(store).captureBatch(anyList());
        var lab=new AiLabService(store,repository,true); lab.run();
        assertEquals(AiLabModel.MODELS.size()*AiLabModel.BUDGET,saved.size());
        for(var f:saved) {
            assertTrue(ForecastProtocol.isProspective(f)); assertEquals(12,((List<?>)f.get("history")).size());
            assertEquals(6,((List<?>)f.get("frequencyNumbers")).size()); assertEquals(6,((List<?>)f.get("randomNumbers")).size());
        }
        for(int i=0;i<5;i++) {
            assertEquals(saved.get(i).get("frequencyNumbers"),saved.get(i+5).get("frequencyNumbers"));
            assertEquals(saved.get(i).get("randomNumbers"),saved.get(i+5).get("randomNumbers"));
        }
        lab.run(); assertEquals(AiLabModel.MODELS.size()*AiLabModel.BUDGET,saved.size()); verify(store,times(AiLabModel.MODELS.size())).captureBatch(anyList());
    }
    @Test void disabledCaptureStillReconcilesAndStaleHistoryDoesNotProduceTickets() {
        var store=mock(AiLabStore.class); var repository=mock(LotteryNumberRepository.class);
        when(store.snapshots()).thenReturn(List.of()); when(repository.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(List.of());
        new AiLabService(store,repository,false).run(); verify(store).reconcile(anyList(),any()); verify(store,never()).captureBatch(anyList());
        new AiLabService(store,repository,true).run(); verify(store,never()).captureBatch(anyList());
    }
    @Test void candidateNeedsTwoFullWindowsPositiveControlsAndNoMissingScheduledDraws() {
        List<Map<String,Object>> snapshots=new ArrayList<>(), events=new ArrayList<>();
        LocalDate target=LocalDate.of(2026,1,4);
        for(int draw=0;draw<60;draw++) {
            List<LotteryNumber> history=new ArrayList<>(); LocalDate date=target.minusDays(1);
            while(history.size()<10) {
                if(Set.of(3,5,7).contains(date.getDayOfWeek().getValue())) history.add(TestDraws.verified(new LotteryNumber(date,"MEGA",List.of(1,2,3,4,5,6),null,null)));
                date=date.minusDays(1);
            }
            Instant created=target.atTime(17,0).atZone(ForecastProtocol.ZONE).toInstant();
            var actual=TestDraws.verified(new LotteryNumber(target,"MEGA",List.of(1,2,3,4,5,6),null,null));
            for(int slot=0;slot<5;slot++) {
                var f=ForecastProtocol.prepare(history,"MEGA","lab-logistic-"+slot,"v1","code",Map.of(),List.of(1,2,3,4,5,6+slot),created);
                f.put("labModel","logistic"); f.put("slot",slot); f.put("frequencyNumbers",List.of(20,21,22,23,24,25)); f.put("randomNumbers",List.of(30,31,32,33,34,35));
                snapshots.add(f); events.add(ForecastProtocol.settle(f,List.of(actual),created.plusSeconds(7200),null));
            }
            target=TemporalRelationshipModel.scheduledAfter(target,"MEGA");
        }
        var report=AiLabService.evaluate(snapshots,events).get(0);
        assertEquals(60,report.get("evaluatedDraws")); assertEquals("VALIDATION_CANDIDATE",report.get("status"));
        var event=events.get(0); event.put("status","RESULT_UNAVAILABLE");
        assertEquals("COLLECTING",AiLabService.evaluate(snapshots,events).get(0).get("status")); event.put("status","SETTLED");
        events.forEach(e->e.put("frequencyMatched",e.get("matched")));
        assertEquals("COLLECTING",AiLabService.evaluate(snapshots,events).get(0).get("status"));
        assertEquals(0.0,AiLabService.evaluate(snapshots,events).get(0).get("frequencyDifferenceLower95"));
    }
}
