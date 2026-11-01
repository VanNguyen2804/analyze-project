package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.PredictionResponseDto;
import com.example.analyzeproject.model.LotteryNumber;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class MegaPortfolioProtocolTest {
    static final Instant BEFORE=Instant.parse("2026-10-25T10:00:00Z");
    static List<LotteryNumber> history() {
        return List.of(2,4,7,9,11,14,16,18,21,23).stream().map(day -> TestDraws.verified(
                new LotteryNumber(LocalDate.of(2026,10,day),"MEGA",List.of(1,2,3,4,5,6),null,null))).toList();
    }
    static PredictionResponseDto prediction() {
        var p=new PredictionResponseDto(); p.setStatus("SUCCESS");p.setCategory("MEGA");
        p.setPrimaryAlgorithmResult(Map.of("ranking",IntStream.rangeClosed(1,45).boxed().toList(),"numbers",List.of(1,2,3,4,5,6)));
        p.setSecondaryAlgorithmResult(Map.of("numbers",List.of(7,8,9,10,11,12)));
        p.setCombinedAlgorithmResult(Map.of("numbers",List.of(1,2,7,8,9,10)));
        p.setTickets(List.of(List.of(1,2,3,4,5,6),List.of(1,2,5,6,7,8),List.of(1,3,5,7,8,9)));
        return p;
    }
    static Map<String,Object> snapshot() { return MegaPortfolioProtocol.prepare(history(),prediction(),"test-code",BEFORE); }
    @SuppressWarnings("unchecked") static Map<String,Object> legacySnapshot() {
        var legacy=new LinkedHashMap<>(snapshot());
        String version=MegaPortfolioProtocol.LEGACY_VERSION;
        legacy.put("id","MEGA:2026-10-25:"+version);legacy.put("algorithm",version);legacy.put("modelVersion",version);
        var ranking=(List<Integer>)legacy.get("ranking");var arms=new LinkedHashMap<>((Map<String,Object>)legacy.get("arms"));
        arms.put("portfolio",TicketPortfolio.select(ranking,45,3));legacy.put("arms",arms);legacy.put("numbers",ranking.subList(0,6).stream().sorted().toList());
        var config=new LinkedHashMap<>((Map<String,Object>)legacy.get("configuration"));config.put("experiment",version);config.remove("duplicatePolicy");config.remove("candidateBudget");
        legacy.put("configuration",config);legacy.put("configurationHash",ForecastProtocol.hash(config));legacy.remove("excludedTickets");
        legacy.put("payloadHash",ForecastProtocol.hash(ForecastProtocol.map("ranking",ranking,"arms",arms)));
        return legacy;
    }
    static LotteryNumber actual(List<Integer> numbers) {
        return TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,25),"MEGA",numbers,null,null));
    }

    @Test @SuppressWarnings("unchecked") void freezesRepeatableEqualBudgetArmsWithoutOriginalDuplicatesAndOnlyMegaBeforeCutoff() {
        var f=snapshot(); assertTrue(MegaPortfolioProtocol.valid(f));assertEquals(f,snapshot());
        var arms=(Map<String,List<List<Integer>>>)f.get("arms");
        for(var tickets:arms.values()) {
            assertEquals(3,tickets.size()); assertEquals(3,new HashSet<>(tickets).size());
            tickets.forEach(t -> {assertEquals(6,new HashSet<>(t).size());assertTrue(t.stream().allMatch(n -> n>=1 && n<=45));});
        }
        for(int i=0;i<3;i++) for(int j=0;j<i;j++) assertTrue(TicketPortfolio.overlap(arms.get("portfolio").get(i),arms.get("portfolio").get(j))<=3);
        var excluded=(List<List<Integer>>)f.get("excludedTickets");
        assertTrue(excluded.contains(List.of(1,2,3,4,5,6)));
        assertTrue(arms.get("portfolio").stream().noneMatch(excluded::contains));
        var tooLate=new LinkedHashMap<>(f);tooLate.put("createdAt","2026-10-25T11:00:00Z");assertFalse(MegaPortfolioProtocol.valid(tooLate));
        var power=new LinkedHashMap<>(f);power.put("category","POWER");assertFalse(MegaPortfolioProtocol.valid(power));
        var changed=new LinkedHashMap<>(f);changed.put("ranking",IntStream.rangeClosed(1,44).boxed().toList());assertFalse(MegaPortfolioProtocol.valid(changed));
        assertThrows(IllegalStateException.class,()->MegaPortfolioProtocol.prepare(history().subList(1,10),prediction(),"code",BEFORE));
        assertThrows(IllegalStateException.class,()->MegaPortfolioProtocol.prepare(history(),prediction(),"code",Instant.parse("2026-10-25T11:00:00Z")));
    }

    @Test void ignoresFutureOrOtherCategoryDrawsAndHistoryOrder() {
        var rows=new ArrayList<>(history());Collections.reverse(rows);
        rows.add(actual(List.of(30,31,32,33,34,35)));
        rows.add(TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,24),"POWER",List.of(40,41,42,43,44,45),null,null)));
        assertEquals(snapshot(),MegaPortfolioProtocol.prepare(rows,prediction(),"test-code",BEFORE));
        var bad=prediction();bad.setTickets(List.of(List.of(1,2,3,4,5,6),List.of(6,5,4,3,2,1),List.of(1,3,5,7,8,9)));
        assertThrows(IllegalStateException.class,()->MegaPortfolioProtocol.prepare(history(),bad,"code",BEFORE));
    }

    @Test @SuppressWarnings("unchecked") void measuresTicketsAndDistinctResultCoverageAndInvalidatesCorrections() {
        var f=snapshot();var actual=actual(List.of(1,2,7,8,11,12));var at=Instant.parse("2026-10-25T12:00:00Z");
        assertEquals("PENDING",MegaPortfolioProtocol.settle(f,List.of(actual),at.minusSeconds(1),null).get("status"));
        var settled=MegaPortfolioProtocol.settle(f,List.of(actual),at,null);
        var wheel=(Map<String,Object>)((Map<?,?>)settled.get("arms")).get("wheel");
        assertEquals(List.of(2,4,3),wheel.get("matches"));assertEquals(4,wheel.get("bestMatched"));
        assertEquals(4,wheel.get("coverageCount"));assertEquals(3.0,wheel.get("averageMatched"));
        var groups=MegaPortfolioProtocol.evaluate(List.of(f),Map.of(f.get("id"),settled));
        assertEquals(1,groups.get(0).get("evaluatedDraws"));
        var summary=((List<Map<String,Object>>)groups.get(0).get("arms")).stream().filter(a -> a.get("id").equals("wheel")).findFirst().orElseThrow();
        assertEquals(1L,summary.get("hit3Draws"));assertEquals(100.0,summary.get("hit3Percent"));
        actual.setNumbers(List.of(30,31,32,33,34,35));
        var unavailable=MegaPortfolioProtocol.settle(f,List.of(actual),at,settled);
        assertEquals("RESULT_UNAVAILABLE",unavailable.get("status"));assertTrue(((Map<?,?>)unavailable.get("arms")).isEmpty());
        assertEquals(0,MegaPortfolioProtocol.evaluate(List.of(f),Map.of(f.get("id"),unavailable)).get(0).get("evaluatedDraws"));
        TestDraws.verified(actual);
        assertEquals("SETTLED",MegaPortfolioProtocol.settle(f,List.of(actual),at,unavailable).get("status"));
    }

    @Test void cohortsKeepTheSamePolicyAcrossDatesAndSeparateCodeChanges() {
        var first=snapshot();var rows=new ArrayList<>(history());rows.add(actual(List.of(1,2,7,8,11,12)));
        var second=MegaPortfolioProtocol.prepare(rows,prediction(),"test-code",Instant.parse("2026-10-26T00:00:00Z"));
        assertEquals(MegaPortfolioProtocol.cohort(first),MegaPortfolioProtocol.cohort(second));
        var groups=MegaPortfolioProtocol.evaluate(List.of(first,second),Map.of());
        assertEquals(1,groups.size());assertEquals(2,groups.get(0).get("savedDraws"));
        var changed=new LinkedHashMap<>(second);changed.put("codeVersion","new-code");
        assertEquals(2,MegaPortfolioProtocol.evaluate(List.of(first,changed),Map.of()).size());
    }
    @Test @SuppressWarnings("unchecked") void realScorerKeepsWheelControlsAndExcludesEveryOriginalTicket() {
        var scorer=new AnalyzeService(org.mockito.Mockito.mock(com.example.analyzeproject.repository.LotteryNumberRepository.class),
                org.mockito.Mockito.mock(com.example.analyzeproject.repository.UserTicketRepository.class),
                org.mockito.Mockito.mock(com.example.analyzeproject.repository.AlgorithmHyperparameterRepository.class),null);
        var prediction=scorer.analyzeAndPredictWithConfiguration("MEGA","temporal_learning",null,history(),null);
        var snapshot=MegaPortfolioProtocol.prepare(history(),prediction,"code",BEFORE);
        var arms=(Map<String,List<List<Integer>>>)snapshot.get("arms");
        assertEquals(prediction.getTickets().subList(0,3),arms.get("wheel"));
        var excluded=(List<List<Integer>>)snapshot.get("excludedTickets");
        assertEquals(((List<List<Integer>>)prediction.getRecommendations().get("diversifiedTickets")).stream().filter(t -> !excluded.contains(t)).limit(3).toList(),arms.get("portfolio"));
        assertTrue(MegaPortfolioProtocol.valid(snapshot));
    }
    @Test @SuppressWarnings("unchecked") void savedModelExclusionsAreOrderIndependentAndNeverReintroduced() {
        var ranking=IntStream.rangeClosed(1,45).boxed().toList();
        var duplicate=TicketPortfolio.select(ranking,45,25).get(1);
        var reversed=new ArrayList<>(duplicate);Collections.reverse(reversed);
        var first=MegaPortfolioProtocol.prepare(history(),prediction(),"test-code",BEFORE,List.of(duplicate,reversed));
        var second=MegaPortfolioProtocol.prepare(history(),prediction(),"test-code",BEFORE,List.of(reversed,duplicate));
        assertEquals(first,second);
        assertFalse(((Map<String,List<List<Integer>>>)first.get("arms")).get("portfolio").contains(duplicate));
        var corrupt=new LinkedHashMap<>(first);corrupt.put("excludedTickets",List.of());
        assertFalse(MegaPortfolioProtocol.valid(corrupt));
    }
    @Test void legacySnapshotsRemainValidAndAreEvaluatedSeparatelyFromNewPolicy() {
        var legacy=legacySnapshot();var current=snapshot();
        assertTrue(MegaPortfolioProtocol.valid(legacy));
        assertEquals(2,MegaPortfolioProtocol.evaluate(List.of(legacy,current),Map.of()).size());
        assertEquals(MegaPortfolioProtocol.LEGACY_VERSION,MegaPortfolioProtocol.row(legacy,null).get("modelVersion"));
    }
}
