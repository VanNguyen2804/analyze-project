package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.service.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConcentratedPortfolioTest {
    @Test void focusedAndHybridPreserveAnchorBudgetDomainAndDeterminism() {
        for(int max:List.of(45,55)) {
            var ranking=IntStream.rangeClosed(1,max).boxed().toList();
            for(var policy:List.of(ConcentratedPortfolio.Policy.focused(),ConcentratedPortfolio.Policy.hybrid())) {
                var tickets=ConcentratedPortfolio.select(ranking,max,5,"fixed",policy);
                assertEquals(tickets,ConcentratedPortfolio.select(ranking,max,5,"fixed",policy));
                assertEquals(5,new HashSet<>(tickets).size());assertEquals(ranking.subList(0,6),tickets.get(0));
                tickets.forEach(t->{assertEquals(6,new HashSet<>(t).size());assertTrue(t.stream().allMatch(n->n>=1&&n<=max));});
                tickets.subList(0,policy.focusedTickets()).forEach(t->assertTrue(t.stream().allMatch(n->n<=12)));
                if(policy.focusedTickets()==3)assertTrue(tickets.subList(3,5).stream().flatMap(Collection::stream).anyMatch(n->n>12));
                var reversed=new ArrayList<>(ranking);Collections.reverse(reversed);
                assertNotEquals(tickets,ConcentratedPortfolio.select(reversed,max,5,"fixed",policy));
            }
            assertThrows(IllegalArgumentException.class,()->ConcentratedPortfolio.select(List.of(),max,5,"fixed",ConcentratedPortfolio.Policy.focused()));
        }
        assertThrows(IllegalArgumentException.class,()->new ConcentratedPortfolio.Policy(17,3,.15));
        assertThrows(IllegalArgumentException.class,()->new ConcentratedPortfolio.Policy(12,3,Double.NaN));
    }
    @Test void observedNovember15BatchSeparatesCoverageAndBestTicketAndIsOrderIndependent() {
        var tickets=List.of(List.of(8,12,24,27,29,35),List.of(7,16,22,27,32,34),List.of(13,17,18,19,21,42),List.of(4,11,26,28,37,44),List.of(1,2,6,10,15,33));
        var ranking=IntStream.rangeClosed(1,45).boxed().toList();
        var report=PortfolioDiagnostics.measure(tickets,List.of(6,22,24,26,40,44),ranking);
        assertEquals(5,report.get("unionHits"));assertEquals(2,report.get("bestTicketHits"));assertEquals(3,report.get("assemblyGap"));
        assertEquals(29,report.get("uniqueNumberCount"));
        assertEquals(false,report.get("conditionalHit5"));
        var reversed=new ArrayList<>(tickets);Collections.reverse(reversed);
        var other=PortfolioDiagnostics.measure(reversed,List.of(6,22,24,26,40,44),ranking);
        for(String key:List.of("unionHits","bestTicketHits","assemblyGap","topKHits"))assertEquals(report.get(key),other.get(key));
        assertNull(PortfolioDiagnostics.measure(tickets,List.of(3,5,9,20,23,30),ranking).get("conditionalHit5"));
    }
}
