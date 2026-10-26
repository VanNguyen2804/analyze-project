package com.example.analyzeproject.service;

import com.example.analyzeproject.model.LotteryNumber;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ForecastProtocolTest {
    private final Instant before = Instant.parse("2026-10-25T10:59:59Z"), after = Instant.parse("2026-10-25T12:00:00Z");
    private List<LotteryNumber> history() {
        var date = LocalDate.of(2026,10,14); List<LotteryNumber> rows = new ArrayList<>();
        for(int i=0;i<5;i++) { rows.add(TestDraws.verified(new LotteryNumber(date,"MEGA",List.of(1,2,3,4,5,6),null,null))); date = TemporalRelationshipModel.scheduledAfter(date,"MEGA"); }
        return rows;
    }
    private Map<String,Object> forecast(String algorithm) { return ForecastProtocol.prepare(history(),"MEGA",algorithm,"v2","code",Map.of("prior",7.5),List.of(1,3,5,7,9,11),before); }
    private LotteryNumber actual(List<Integer> numbers) { return TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,25),"MEGA",numbers,null,null)); }
    @Test void captureHasCutoffAndRequiresFreshHistory() {
        assertEquals("2026-10-25",forecast("temporal_learning").get("targetDate"));
        assertEquals(LocalDate.of(2026,10,28),ForecastProtocol.target("MEGA",Instant.parse("2026-10-25T11:00:00Z")));
        assertThrows(IllegalStateException.class,()->ForecastProtocol.prepare(history().subList(0,4),"MEGA","a","v2","code",Map.of(),List.of(1,2,3,4,5,6),before));
        assertThrows(IllegalArgumentException.class,()->ForecastProtocol.prepare(history(),"MEGA","a","v2","code",Map.of(),List.of(1,1,2,3,4,5),before));
        var rows = history(); rows.add(actual(List.of(10,11,12,13,14,15)));
        assertEquals(5,ForecastProtocol.training(rows,"MEGA",before).size());
    }
    @Test void resultsAndTrainingWaitUntilNineteenVietnamTime() {
        var beforeUpdate = Instant.parse("2026-10-25T11:59:59Z");
        var updateTime = Instant.parse("2026-10-25T12:00:00Z");
        var result = actual(List.of(1,3,5,20,21,22));
        var rows = history(); rows.add(result);
        assertEquals(LocalDate.of(2026,10,23),ForecastProtocol.expected("MEGA",beforeUpdate));
        assertEquals(LocalDate.of(2026,10,25),ForecastProtocol.expected("MEGA",updateTime));
        assertEquals(5,ForecastProtocol.training(rows,"MEGA",beforeUpdate).size());
        assertEquals(6,ForecastProtocol.training(rows,"MEGA",updateTime).size());
        assertEquals("PENDING",ForecastProtocol.settle(forecast("temporal_learning"),rows,beforeUpdate,null).get("status"));
        assertEquals("SETTLED",ForecastProtocol.settle(forecast("temporal_learning"),rows,updateTime,null).get("status"));
        assertEquals(LocalDate.of(2026,10,28),ForecastProtocol.target("MEGA",beforeUpdate));
    }
    @Test void controlTicketsDoNotDependOnAlgorithmOrTargetResult() {
        var a = forecast("temporal_learning"); var b = forecast("bayesian_graph");
        assertEquals(List.of(1,2,3,4,5,6),a.get("frequencyNumbers"));
        assertEquals(a.get("randomNumbers"),b.get("randomNumbers"));
        assertEquals(List.of(1,4,10,18,24,35),a.get("randomNumbers")); // Same protocol control as Node.
        assertEquals(6,new HashSet<>((List<?>)a.get("randomNumbers")).size());
        assertNotNull(ForecastProtocol.codeVersion());
    }
    @Test void waitsForPublicationAndInvalidatesUnverifiedResultRevision() {
        var forecast = forecast("temporal_learning"); var actual = actual(List.of(1,3,5,20,21,22));
        assertEquals("PENDING",ForecastProtocol.settle(forecast,List.of(actual),before,null).get("status"));
        var settled = ForecastProtocol.settle(forecast,List.of(actual),after,null);
        assertEquals(3,settled.get("matched"));
        actual.setNumbers(List.of(7,8,9,10,11,12));
        var invalid = ForecastProtocol.settle(forecast,List.of(actual),after,settled);
        assertEquals("RESULT_UNAVAILABLE",invalid.get("status"));
        assertNull(invalid.get("matched"));
        TestDraws.verified(actual);
        assertEquals("SETTLED",ForecastProtocol.settle(forecast,List.of(actual),after,invalid).get("status"));
    }
    @Test void evaluationUsesLatestSettlementAndSeparatesModelVersions() {
        var a=forecast("temporal_learning"); var b=new LinkedHashMap<>(a); b.put("id","second"); b.put("modelVersion","v3");
        var result = ForecastProtocol.settle(a,List.of(actual(List.of(1,3,5,20,21,22))),after,null);
        var evaluation = ForecastProtocol.evaluate(List.of(a,b),List.of(result),null);
        List<?> groups=(List<?>)evaluation.get("groups");
        assertEquals(2,groups.size());
        assertEquals(3.0,((Map<?,?>)groups.get(0)).get("averageMatched"));
        assertEquals(0,((Map<?,?>)groups.get(1)).get("evaluatedDraws"));
        assertNull(((Map<?,?>)groups.get(1)).get("averageMatched"));
        var invalid = ForecastProtocol.settle(a,List.of(),after,result);
        var latest = ForecastProtocol.evaluate(List.of(a),List.of(result,invalid),null);
        assertEquals(0,((Map<?,?>)((List<?>)latest.get("groups")).get(0)).get("evaluatedDraws"));
        assertTrue(((List<?>)ForecastProtocol.evaluate(List.of(),List.of(),null).get("groups")).isEmpty());
        var tooLate = new LinkedHashMap<>(a); tooLate.put("createdAt","2026-10-25T11:00:00Z");
        var excluded = ForecastProtocol.evaluate(List.of(tooLate),List.of(result),null);
        assertEquals(1,excluded.get("excludedInvalidSnapshots"));
        assertTrue(((List<?>)excluded.get("groups")).isEmpty());
    }
}
