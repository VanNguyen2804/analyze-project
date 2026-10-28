package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.WorkspaceRequests.*;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.example.analyzeproject.service.ForecastProtocol.map;

class WorkspaceTest {
    private final List<Integer> ranking=IntStream.rangeClosed(1,45).boxed().toList();
    @Test void weightedFusionUsesFullRankingsAndEnforcesUserConstraints() {
        var reverse=new ArrayList<>(ranking); Collections.reverse(reverse);
        assertEquals(List.of(1,2,3,4,5,6),WorkspaceProtocol.combine("MEGA",ranking,reverse,1,List.of(),List.of()));
        assertEquals(List.of(40,41,42,43,44,45),WorkspaceProtocol.combine("MEGA",ranking,reverse,0,List.of(),List.of()));
        var result=WorkspaceProtocol.combine("MEGA",ranking,reverse,.5,List.of(22,23),List.of(1,45));
        assertTrue(result.containsAll(List.of(22,23))); assertFalse(result.contains(1)); assertFalse(result.contains(45));
        assertEquals(6,new HashSet<>(result).size());
        assertEquals(result,WorkspaceProtocol.combine("MEGA",reverse,ranking,.5,List.of(22,23),List.of(1,45)));
        assertThrows(IllegalArgumentException.class,()->WorkspaceProtocol.combine("MEGA",ranking,reverse,Double.NaN,List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->WorkspaceProtocol.combine("MEGA",ranking,reverse,.5,List.of(1),List.of(1)));
        assertThrows(IllegalArgumentException.class,()->WorkspaceProtocol.combine("MEGA",ranking.subList(0,6),reverse,.5,List.of(),List.of()));
    }
    @Test void coverageDoesNotAddRepeatedHitsAndControlsHaveTheSameBudget() {
        var actual=List.of(4,10,14,22,25,44);
        var result=WorkspaceProtocol.metrics(List.of(List.of(8,9,10,12,22,27),List.of(6,14,21,25,33,40)),actual);
        assertEquals(List.of(2,2),result.get("matches")); assertEquals(4,result.get("coverageCount")); assertEquals(2,result.get("bestMatched"));
        assertEquals(2,WorkspaceProtocol.metrics(List.of(List.of(8,9,10,12,22,27),List.of(1,2,3,10,22,45)),actual).get("coverageCount"));
        var rows=history("MEGA",LocalDate.of(2026,10,23));
        var controls=WorkspaceProtocol.controls(rows,"MEGA",8,"fixed-before-result",true);
        assertEquals(8,new HashSet<>(controls).size()); controls.forEach(t->WorkspaceProtocol.numbers("MEGA",t));
        assertEquals(controls,WorkspaceProtocol.controls(rows,"MEGA",8,"fixed-before-result",true));
        assertThrows(IllegalArgumentException.class,()->WorkspaceProtocol.numbers("POWER",List.of(7,11,113,18,24,25)));
        assertThrows(IllegalArgumentException.class,()->WorkspaceProtocol.numbers("POWER",List.of(0,0,0,0,0,0)));
    }
    @Test void nextDrawCannotFreezeWithMissingPreviousResultBetweenEighteenAndNineteen() {
        var rows=history("MEGA",LocalDate.of(2026,10,23));
        Instant gap=Instant.parse("2026-10-25T11:30:00Z");
        assertEquals(LocalDate.of(2026,10,28),ForecastProtocol.target("MEGA",gap));
        assertFalse(WorkspaceProtocol.ready(rows,"MEGA",ForecastProtocol.target("MEGA",gap)));
        assertThrows(IllegalStateException.class,()->ForecastProtocol.prepare(rows,"MEGA","a","v","code",Map.of(),List.of(1,2,3,4,5,6),gap));
        rows.add(TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,25),"MEGA",List.of(4,13,22,25,40,41),null,null)));
        assertEquals(12,ForecastProtocol.training(rows,"MEGA",Instant.parse("2026-10-25T11:59:59Z")).size());
        assertTrue(WorkspaceProtocol.ready(ForecastProtocol.training(rows,"MEGA",Instant.parse("2026-10-25T12:00:00Z")),"MEGA",LocalDate.of(2026,10,28)));
        assertEquals(LocalDate.of(2026,12,30),WorkspaceProtocol.previous("MEGA",LocalDate.of(2027,1,1)));
    }
    @Test void labProjectionUsesExactTargetStableSlotsAndPreservesAllDuplicateOrigins() {
        var history=history("MEGA",LocalDate.of(2026,10,25)); var snapshots=new ArrayList<Map<String,Object>>();
        Instant captured=Instant.parse("2026-10-26T08:00:00Z");
        for(String model:AiLabModel.MODELS) for(int slot=0;slot<5;slot++) {
            var nums=List.of(1,2,3,4,5,6+slot);
            var f=ForecastProtocol.prepare(history,"MEGA","lab-"+model+"-"+slot,AiLabModel.VERSION,"code",Map.of(),nums,captured);
            f.put("labModel",model); f.put("slot",slot); snapshots.add(f);
        }
        var lab=mock(AiLabStore.class); when(lab.snapshots()).thenReturn(snapshots);
        var service=new WorkspaceService(mock(AnalyzeService.class),mock(LotteryNumberRepository.class),lab,mock(WorkspaceStore.class));
        var result=service.labSuggestions("MEGA","2026-10-28",history);
        var tickets=(List<?>)result.get("tickets"); assertEquals(5,tickets.size());
        assertEquals(2,((List<?>)((Map<?,?>)tickets.get(0)).get("labOrigins")).size());
        Collections.reverse(snapshots); assertEquals(result,service.labSuggestions("MEGA","2026-10-28",history));
        assertTrue(((List<?>)service.labSuggestions("MEGA","2026-10-30",history).get("tickets")).isEmpty());
        snapshots.remove(0); assertEquals(1,((List<?>)((Map<?,?>)((List<?>)service.labSuggestions("MEGA","2026-10-28",history).get("tickets")).get(0)).get("labOrigins")).size());
    }
    @Test void settlementWaitsThenSeparatesOriginalUserAndEqualBudgetControlsAndInvalidatesCorrections() {
        var ticket=map("numbers",List.of(1,2,3,4,5,6),"originalNumbers",List.of(10,11,12,13,14,15));
        var batch=map("id","batch","category","MEGA","targetDate","2026-10-28","tickets",List.of(ticket),
                "frequencyTickets",List.of(List.of(1,7,8,9,10,11)),"randomTickets",List.of(List.of(1,20,21,22,23,24)),
                "suggestion",map("tickets",List.of(map("source","ALGORITHM","numbers",List.of(10,11,12,13,14,15)))));
        var draw=TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,28),"MEGA",List.of(1,2,3,30,31,32),null,null));
        assertEquals("PENDING",WorkspaceService.settle(batch,List.of(draw),Instant.parse("2026-10-28T11:59:59Z"),null).get("status"));
        var result=WorkspaceService.settle(batch,List.of(draw),Instant.parse("2026-10-28T12:00:00Z"),null);
        assertEquals(3,((Map<?,?>)result.get("user")).get("bestMatched")); assertEquals(0,((Map<?,?>)result.get("originalSelected")).get("bestMatched"));
        assertEquals(1,((Map<?,?>)result.get("frequency")).get("ticketCount"));
        draw.setNumbers(List.of(1,2,3,4,5,6));
        var invalid=WorkspaceService.settle(batch,List.of(draw),Instant.parse("2026-10-28T12:01:00Z"),result);
        assertEquals("RESULT_UNAVAILABLE",invalid.get("status")); assertNull(invalid.get("user"));
    }
    static List<LotteryNumber> history(String category,LocalDate through) {
        var rows=new ArrayList<LotteryNumber>(); LocalDate date=through;
        for(int i=0;i<12;i++) { rows.add(TestDraws.verified(new LotteryNumber(date,category,List.of(1,2,3,4,5,6),null,null))); date=WorkspaceProtocol.previous(category,date); }
        Collections.reverse(rows); return rows;
    }
}
