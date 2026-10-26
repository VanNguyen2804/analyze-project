package com.example.analyzeproject.service;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.model.UserTicket;
import com.example.analyzeproject.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemporalRelationshipModelTest {
    private List<LotteryNumber> alternating(int count) {
        List<LotteryNumber> draws = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 8, 1);
        for (int i = 0; i < count; i++) {
            draws.add(new LotteryNumber(date, "POWER", i % 2 == 0 ? List.of(1,2,3,4,5,6) : List.of(20,21,22,23,24,25), 55, null));
            date = TemporalRelationshipModel.scheduledAfter(date, "POWER");
        }
        draws.forEach(TestDraws::verified);
        return draws;
    }

    @Test void learnsTransitionsAndRemainsDeterministic() {
        List<LotteryNumber> draws = alternating(30);
        var model = TemporalRelationshipModel.fit(draws, "POWER");
        assertEquals(List.of(1,2,3,4,5,6), model.analysis().get("predictedNumbers"));
        Collections.reverse(draws);
        assertArrayEquals(model.scores(), TemporalRelationshipModel.fit(draws, "POWER").scores());
        assertTrue(Arrays.stream(model.scores()).skip(1).allMatch(p -> Double.isFinite(p) && p > 0 && p < 1));
    }

    @Test void validationReplaysForecastsBeforeObservingEachResult() {
        List<LotteryNumber> draws = alternating(18);
        var model = TemporalRelationshipModel.fit(draws, "POWER");
        int matched = 0;
        double loss = 0;
        for (int t = 5; t < draws.size(); t++) {
            var before = TemporalRelationshipModel.fit(draws.subList(0, t), "POWER");
            List<Integer> numbers = draws.get(t).getNumbers();
            List<Integer> top = IntStream.rangeClosed(1,55).boxed()
                    .sorted(Comparator.<Integer>comparingDouble(n -> before.scores()[n]).reversed().thenComparingInt(n -> n)).limit(6).toList();
            matched += (int) top.stream().filter(numbers::contains).count();
            for (int n = 1; n <= 55; n++) loss += Math.pow(before.scores()[n] - (numbers.contains(n) ? 1 : 0), 2) / 55;
        }
        Map<?, ?> evaluation = (Map<?, ?>) model.analysis().get("evaluation");
        assertEquals(13, evaluation.get("evaluatedDraws"));
        assertEquals(matched / 13.0, (double) evaluation.get("averageMatched"), 1e-12);
        assertEquals(loss / 13, (double) evaluation.get("brierScore"), 1e-12);
    }

    @Test void missingDrawsAreNotLearnedAsConsecutive() {
        List<LotteryNumber> history = alternating(3);
        var model = TemporalRelationshipModel.fit(List.of(history.get(0), history.get(2)), "POWER");
        assertEquals(1, model.analysis().get("skippedGapTransitions"));
        assertNull(((Map<?, ?>) model.analysis().get("evaluation")).get("hit3RatePercent"));
        assertTrue(((List<?>) model.analysis().get("topTransitions")).isEmpty());
        assertEquals(List.of(), TemporalRelationshipModel.fit(List.of(), "POWER").analysis().get("predictedNumbers"));
    }

    @Test void reportsTicketObjectiveWeightsAndMeasuredHistogram() {
        var result = TemporalRelationshipModel.fit(alternating(22), "POWER").analysis();
        Map<?, ?> evaluation = (Map<?, ?>) result.get("evaluation");
        assertEquals(17, Arrays.stream((int[]) evaluation.get("matchHistogram")).sum());
        assertEquals(36.0 / 55, evaluation.get("randomExpectedAverageMatched"));
        List<Map<String, Object>> patterns = (List<Map<String, Object>>) result.get("patterns");
        assertEquals(9, patterns.size());
        Map<String, Object> frequency = patterns.get(0);
        for (var expert : patterns) {
            double difference = ((double) expert.get("averageMatched") - (double) frequency.get("averageMatched")) * 17;
            assertEquals(Math.exp(0.5 * difference / 6), (double) expert.get("weightPercent") / (double) frequency.get("weightPercent"), 1e-12);
        }
    }

    @Test void serviceUsesTheLearnedTicketAndRecomputesUserMatches() {
        LotteryNumberRepository repository = mock(LotteryNumberRepository.class);
        UserTicketRepository tickets = mock(UserTicketRepository.class);
        AnalyzeService service = new AnalyzeService(repository, tickets, mock(FrenchExerciseRepository.class),
                mock(FrenchAttemptRepository.class), mock(AlgorithmHyperparameterRepository.class), null);
        List<LotteryNumber> history = alternating(12);
        history.add(new LotteryNumber(LocalDate.of(2026,10,24), "POWER", List.of(6,7,18,41,44,50), 53, "User-supplied"));
        history.add(new LotteryNumber(LocalDate.of(2026,10,27), "POWER", List.of(1,2,3,4,5,6), 55, "Future test fixture"));
        history.forEach(TestDraws::verified);
        List<UserTicket> saved = new ArrayList<>();
        for (List<Integer> numbers : List.of(List.of(7,13,22,24,45,52), List.of(8,10,14,17,25,48), List.of(6,7,11,13,18,25))) {
            UserTicket ticket = new UserTicket(); ticket.setCategory("POWER"); ticket.setDrawDate("2026-10-24");
            ticket.setNumbers(numbers); ticket.setPrize("Untrusted cached label"); saved.add(ticket);
        }
        when(repository.findByCategoryOrderByDrawDateDescCreatedAtDesc("POWER")).thenReturn(history);
        when(tickets.findAllByOrderByCheckedAtDesc()).thenReturn(saved);
        Map<String, Object> report = service.getRelationshipLearning("POWER", "2026-10-24");
        assertEquals("2026-10-24", report.get("asOfDate"));
        List<?> winners = (List<?>) report.get("verifiedWinningTickets");
        assertEquals(1, winners.size());
        Map<?, ?> winner = (Map<?, ?>) winners.get(0);
        assertEquals(List.of(6,7,18), winner.get("matchedNumbers"));
        assertEquals(3, winner.get("matchedCount"));
        var response = service.analyzeAndPredict("POWER", "temporal_learning", "bayesian_graph", history);
        assertEquals(response.getRelationshipLearning().get("predictedNumbers"), response.getPrimaryAlgorithmResult().get("numbers"));
        assertEquals("DRAW_NOT_FOUND", service.getRelationshipLearning("POWER", "2026-10-22").get("status"));
    }
}
