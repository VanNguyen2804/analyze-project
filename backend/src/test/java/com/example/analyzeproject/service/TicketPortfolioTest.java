package com.example.analyzeproject.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class TicketPortfolioTest {
    @Test void portfoliosAreValidRepeatableAndPreserveThePrimaryTicket() {
        for (int max : List.of(45, 55)) {
            for (int seed = 0; seed < 4; seed++) {
                List<Integer> ranking = new ArrayList<>(IntStream.rangeClosed(1, max).boxed().toList());
                Collections.shuffle(ranking, new Random(seed));
                var tickets = TicketPortfolio.select(ranking, max, 25);
                assertEquals(tickets, TicketPortfolio.select(ranking, max, 25));
                assertEquals(25, new HashSet<>(tickets).size());
                assertEquals(ranking.subList(0, 6).stream().sorted().toList(), tickets.get(0));
                for (int i = 0; i < tickets.size(); i++) {
                    assertEquals(6, new HashSet<>(tickets.get(i)).size());
                    assertTrue(tickets.get(i).stream().allMatch(n -> n >= 1 && n <= max));
                    for (int j = 0; j < i; j++) assertTrue(TicketPortfolio.overlap(tickets.get(i), tickets.get(j)) <= 3);
                }
                assertTrue(tickets.subList(0, 3).stream().flatMap(Collection::stream).distinct().count() > 10);
                assertEquals(tickets.subList(0, 3), TicketPortfolio.select(ranking, max, 3));
            }
        }
    }

    @Test void invalidBudgetsAreRejectedAndIncompleteRankingsAreCompleted() {
        assertThrows(IllegalArgumentException.class, () -> TicketPortfolio.select(List.of(), 55, 0));
        assertThrows(IllegalArgumentException.class, () -> TicketPortfolio.select(List.of(), 55, 26));
        assertEquals(3, TicketPortfolio.select(Arrays.asList(null, 0, 1, 1, 56), 55, 3).size());
    }
}
