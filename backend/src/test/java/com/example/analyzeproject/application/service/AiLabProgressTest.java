package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.service.AiLabProgress;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiLabProgressTest {
    @Test void accumulatesOneAndTwoMatchesWithCorrectTicketAndDrawDenominators() {
        var first=new AiLabProgress.Draw("2026-10-25",List.of(new AiLabProgress.Ticket(0,1,0),new AiLabProgress.Ticket(1,1,0),new AiLabProgress.Ticket(2,1,0),new AiLabProgress.Ticket(1,1,0),new AiLabProgress.Ticket(0,1,0)));
        var second=new AiLabProgress.Draw("2026-10-28",Collections.nCopies(5,new AiLabProgress.Ticket(1,0,1)));
        var report=AiLabProgress.summarize(List.of(second,first));
        assertEquals(9,report.totalMatched());assertEquals(10,report.evaluatedTickets());assertEquals(.9,report.averageMatchedPerTicket());
        assertEquals(2,report.bestTicketMatched());assertEquals(List.of(2,7,1,0,0,0,0),report.matchHistogram());
        assertEquals(80.0,report.levels().get(0).ticketPercent());assertEquals(100.0,report.levels().get(0).drawPercent());
        assertEquals(10.0,report.levels().get(1).ticketPercent());assertEquals(50.0,report.levels().get(1).drawPercent());
        assertEquals(0.0,report.levels().get(2).ticketPercent());assertEquals(.5,report.frequencyAverageMatched());
        assertEquals(4,report.timeline().get(0).cumulativeMatched());assertEquals(9,report.timeline().get(1).cumulativeMatched());
        assertEquals(.9,report.recent30AverageMatched());assertNull(report.previous30AverageMatched());
    }
    @Test void emptyDataDoesNotInventPercentagesAndIncompleteBatchesCannotInflateTotals() {
        var report=AiLabProgress.summarize(List.of());assertNull(report.averageMatchedPerTicket());assertNull(report.levels().get(0).ticketPercent());
        assertTrue(report.timeline().isEmpty());assertEquals(0,report.totalMatched());
        assertThrows(IllegalArgumentException.class,()->AiLabProgress.summarize(List.of(new AiLabProgress.Draw("2026-10-25",List.of(new AiLabProgress.Ticket(2,0,0))))));
    }
}
