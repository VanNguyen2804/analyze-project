package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.DrawReport.*;
import com.example.analyzeproject.model.*;
import com.example.analyzeproject.repository.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.example.analyzeproject.service.ForecastProtocol.map;

class DrawAnalysisServiceTest {
    final LotteryNumberRepository draws=mock(LotteryNumberRepository.class);
    final UserTicketRepository users=mock(UserTicketRepository.class);
    final ForecastStore forecasts=mock(ForecastStore.class);
    final AiLabStore lab=mock(AiLabStore.class);
    final WorkspaceStore workspace=mock(WorkspaceStore.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-10-29T14:40:00Z"),ZoneOffset.UTC);
    final DrawAnalysisService service=new DrawAnalysisService(draws,users,forecasts,lab,workspace,clock);
    final LotteryNumber actual=TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,29),"POWER",List.of(9,29,33,38,45,46),53,null));
    final List<List<Integer>> tickets=List.of(List.of(7,11,14,18,23,27),List.of(7,9,18,27,31,50),List.of(7,14,18,27,31,50),List.of(9,12,14,36,37,51),List.of(8,15,29,33,43,49));
    @BeforeEach void setup() {
        when(draws.findByCategoryOrderByDrawDateDescCreatedAtDesc("POWER")).thenReturn(List.of(actual));
        when(workspace.history("POWER")).thenReturn(map("batches",List.of()));
    }
    List<LotteryNumber> history() {
        return List.of(17,20,22,24,27).stream().map(day->TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,day),"POWER",List.of(7,9,18,27,31,50),53,null))).toList();
    }
    Map<String,Object> snapshot(String algorithm,List<Integer> ticket) {
        return ForecastProtocol.prepare(history(),"POWER",algorithm,"model-v1","scoring-v1",Map.of("version","frozen"),ticket,Instant.parse("2026-10-27T13:00:00Z"));
    }
    @Test void fiveTicketsUseOneArithmeticAndCheckingDoesNotPersistAnything() {
        var result=service.evaluateTickets(new EvaluateRequest("POWER","2026-10-29",tickets));
        assertEquals(List.of(0,1,0,1,2),result.tickets().stream().map(TicketResult::matchedCount).toList());
        var p=result.portfolio(); assertEquals(19,p.uniqueNumberCount()); assertEquals(4,p.rawMatchedOccurrences());
        assertEquals(List.of(9,29,33),p.coverage()); assertEquals(2,p.bestMatched()); assertEquals(5,p.overlaps().get(1).get(2));
        verifyNoInteractions(users,forecasts,lab,workspace);
        assertThrows(IllegalArgumentException.class,()->service.evaluateTickets(new EvaluateRequest("POWER","2026-10-29",List.of(List.of(7,7,9,10,11,12)))));
        assertThrows(IllegalArgumentException.class,()->service.evaluateTickets(new EvaluateRequest("POWER","2026-10-29",List.of(List.of(1,2,3,4,5,113)))));
    }
    @Test void legacyForecastRemainsCountableButItsMissingRanksAreNotReconstructed() {
        when(forecasts.forecasts()).thenReturn(List.of(snapshot("temporal_learning",tickets.get(0)),snapshot("bayesian_graph",tickets.get(1))));
        var report=service.report("POWER","2026-10-29","bayesian_graph",null);
        assertEquals(1,report.evidence().size()); assertEquals(tickets.get(1),report.evidence().get(0).numbers());
        assertEquals("NOT_SAVED",report.evidence().get(0).explanationStatus());
        assertTrue(report.numberDetails().stream().allMatch(n->n.rank()==null&&n.score()==null));
        assertEquals(1,report.ticketGroups().get(0).portfolio().bestMatched());
        assertThrows(IllegalArgumentException.class,()->service.report("POWER","2026-10-29","bayesian_graph","POWER:2026-10-29:temporal_learning"));
        verify(forecasts,never()).capture(any()); verify(workspace,never()).saveSuggestion(any()); verify(users,never()).save(any());
    }
    @Test void frozenScoresDoNotChangeWhenLaterDrawsArriveAndFutureTicketsAreExcluded() {
        var f=snapshot("temporal_learning",tickets.get(0));
        var ranking=java.util.stream.IntStream.rangeClosed(1,55).boxed().toList();
        f.put("explanation",map("ranking",ranking,"scoreType","internal_score","details",List.of(map("number",9,"score",0.125))));
        var future=snapshot("temporal_learning",tickets.get(4)); future.put("targetDate","2026-10-31"); future.put("id","future");
        when(forecasts.forecasts()).thenReturn(List.of(f,future));
        var before=service.report("POWER","2026-10-29","temporal_learning",null);
        assertEquals(9,before.numberDetails().get(8).rank()); assertEquals(0.125,before.numberDetails().get(8).score());
        var later=TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,31),"POWER",List.of(1,2,3,4,5,6),7,null));
        when(draws.findByCategoryOrderByDrawDateDescCreatedAtDesc("POWER")).thenReturn(List.of(later,actual));
        var after=service.report("POWER","2026-10-29","temporal_learning",null);
        assertEquals(before.evidence(),after.evidence()); assertEquals(1,after.evidence().size());
        assertEquals(1,after.evaluation().get(0).evaluatedDraws());
    }
    @Test void checkedTicketsNeverAcquireModelProvenanceAndInvalidRowsAreAudited() {
        UserTicket valid=new UserTicket();valid.setId(79L);valid.setCategory("POWER");valid.setDrawDate("2026-10-29");valid.setNumbers(tickets.get(0));
        UserTicket bad=new UserTicket();bad.setCategory("POWER");bad.setDrawDate("2026-10-29");bad.setNumbers(List.of(1,2,3,4,5,113));
        when(users.findAllByOrderByCheckedAtDesc()).thenReturn(List.of(valid,bad));
        var group=service.report("POWER","2026-10-29","temporal_learning",null).ticketGroups().get(0);
        assertEquals(1,group.invalidTicketCount()); assertEquals(1,group.tickets().size());
        assertNull(group.tickets().get(0).sourceId()); assertEquals("CHECKED_AFTER_DRAW",group.timing());
        verify(users,never()).delete(any()); verify(users,never()).save(any());
    }
    @Test void resultRevisionAndVietnamPublicationBoundaryAreRespected() {
        for(String time:List.of("2026-10-29T10:59:59Z","2026-10-29T11:00:00Z","2026-10-29T11:59:59Z")) {
            var early=new DrawAnalysisService(draws,users,forecasts,lab,workspace,Clock.fixed(Instant.parse(time),ZoneOffset.UTC));
            var r=early.report("POWER","2026-10-29","temporal_learning",null); assertEquals("PENDING",r.status());assertNull(r.draw());
        }
        var onTime=new DrawAnalysisService(draws,users,forecasts,lab,workspace,Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"),ZoneOffset.UTC));
        assertEquals("SUCCESS",onTime.report("POWER","2026-10-29","temporal_learning",null).status());
        var quarantined=new LotteryNumber(actual.getDrawDate(),"POWER",actual.getNumbers(),53,null);quarantined.setCreatedAt(actual.getCreatedAt().plusSeconds(1));
        quarantined.setQuality(map("status","QUARANTINED"));
        when(draws.findByCategoryOrderByDrawDateDescCreatedAtDesc("POWER")).thenReturn(List.of(actual,quarantined));
        assertEquals("RESULT_UNAVAILABLE",service.report("POWER","2026-10-29","temporal_learning",null).status());
        assertThrows(IllegalArgumentException.class,()->service.evaluateTickets(new EvaluateRequest("POWER","2026-10-29",tickets)));
        assertThrows(IllegalArgumentException.class,()->service.report("POWER","2026-02-30","temporal_learning",null));
    }
    @Test void duplicateTicketsDoNotIncreaseBudgetOrCoverageAndPowerUsesOnlySixNumbers() {
        var duplicated=service.evaluateTickets(new EvaluateRequest("POWER","2026-10-29",List.of(tickets.get(4),tickets.get(4))));
        assertEquals(1,duplicated.portfolio().duplicateTicketCount());assertEquals(1,duplicated.portfolio().ticketCount());assertEquals(2,duplicated.portfolio().coverageCount());
        var jackpot=TicketAnalysis.evaluate("POWER",List.of(9,29,33,38,45,53),actual.getNumbers(),53,"","","","",null,false);
        assertEquals("JACKPOT 2",jackpot.prize());assertEquals(5,jackpot.matchedCount());
        assertEquals("JACKPOT 1",TicketAnalysis.prize("POWER",6,false));assertEquals("JACKPOT",TicketAnalysis.prize("MEGA",6,false));
        var mega=List.of(10,14,22,25,30,40);
        var a=TicketAnalysis.evaluate("MEGA",List.of(1,2,3,4,10,22),mega,null,"","","","",null,false);
        var b=TicketAnalysis.evaluate("MEGA",List.of(1,2,3,4,14,25),mega,null,"","","","",null,false);
        assertEquals(4,TicketAnalysis.portfolio(List.of(a,b)).coverageCount());assertEquals(2,TicketAnalysis.portfolio(List.of(a,b)).bestMatched());
    }
    @Test void malformedAndPostDrawSnapshotsCannotBeUsedAsHistoricalEvidence() {
        var late=snapshot("temporal_learning",tickets.get(0));late.put("createdAt","2026-10-29T11:00:00Z");
        var leaked=snapshot("temporal_learning",tickets.get(0));leaked.put("trainingThrough","2026-10-29");
        var corrupted=snapshot("temporal_learning",tickets.get(0));corrupted.put("historyHash","changed");
        when(forecasts.forecasts()).thenReturn(List.of(late,leaked,corrupted));
        assertTrue(service.report("POWER","2026-10-29","temporal_learning",null).evidence().isEmpty());
    }
    @Test void activeUserRevisionUsesTheSameBudgetAndOnlyValidOriginalEditedPairs() {
        var committed=map("id","active","active",true,"category","POWER","targetDate","2026-10-29","committedAt","2026-10-29T10:00:00Z","revision",2,
                "protocol",WorkspaceProtocol.VERSION,"codeVersion","code",
                "tickets",List.of(map("numbers",tickets.get(4),"originalNumbers",tickets.get(0),"edited",true,"sourceId","primary"),map("numbers",tickets.get(1),"source","MANUAL")),
                "frequencyTickets",List.of(tickets.get(0),tickets.get(1)),"randomTickets",List.of(tickets.get(3),tickets.get(4)));
        var superseded=new LinkedHashMap<>(committed);superseded.put("id","old");superseded.put("active",false);superseded.put("revision",1);
        when(workspace.history("POWER")).thenReturn(map("batches",List.of(committed,superseded)));
        var report=service.report("POWER","2026-10-29","temporal_learning",null);
        assertEquals(1,report.ticketGroups().size());var group=report.ticketGroups().get(0);
        assertEquals(2,group.portfolio().ticketCount());assertEquals(2,group.frequency().ticketCount());
        assertEquals(1,group.comparison().pairedTicketCount());assertEquals(0,group.comparison().originalAverageMatched());assertEquals(2,group.comparison().finalAverageMatched());
        var evaluation=report.evaluation().get(0);assertEquals("WORKSPACE_USER",evaluation.source());assertEquals(2,evaluation.ticketBudget());assertEquals(1,evaluation.evaluatedDraws());assertEquals(1,evaluation.pairedDraws());
    }
    @Test void labEvaluationRequiresCompleteDistinctSlotsAndNeverSelectsTheBestTicketAfterTheResult() {
        List<Map<String,Object>> batch=new ArrayList<>();
        for(int i=0;i<5;i++) { var f=snapshot("lab-logistic-"+i,tickets.get(i));f.put("labModel","logistic");f.put("slot",i);batch.add(f); }
        when(lab.snapshots()).thenReturn(batch);
        var evaluation=service.report("POWER","2026-10-29","lab-logistic",null).evaluation().get(0);
        assertEquals(5,evaluation.ticketBudget());assertEquals(1,evaluation.evaluatedDraws());assertEquals(0.8,evaluation.averageMatched());assertEquals(3,evaluation.coverageAverage());
        batch.get(4).put("slot",3);
        assertEquals(0,service.report("POWER","2026-10-29","lab-logistic",null).evaluation().get(0).evaluatedDraws());
    }
}
