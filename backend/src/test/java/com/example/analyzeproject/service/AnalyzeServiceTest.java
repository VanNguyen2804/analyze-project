package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.PredictionResponseDto;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyzeServiceTest {
    private final LotteryNumberRepository repository = mock(LotteryNumberRepository.class);
    private final AlgorithmHyperparameterRepository parameters = mock(AlgorithmHyperparameterRepository.class);
    private final AnalyzeService service = new AnalyzeService(repository, mock(UserTicketRepository.class),
            mock(FrenchExerciseRepository.class), mock(FrenchAttemptRepository.class), parameters, null);

    private List<LotteryNumber> history(String category, int offset) {
        List<LotteryNumber> result = new ArrayList<>();
        int max = category.equals("POWER") ? 55 : 45;
        for (int i = 0; i < 15; i++) {
            List<Integer> numbers = new ArrayList<>();
            for (int j = 0; j < 6; j++) numbers.add((i * 3 + j * 7 + offset) % max + 1);
            result.add(new LotteryNumber(LocalDate.of(2026, 9, i + 1), category, numbers, null, null));
        }
        return result;
    }

    @Test void emptyHistoryDoesNotReadLiveDrawsOrInventTickets() {
        PredictionResponseDto response = service.analyzeAndPredict("MEGA", "bayesian_graph", Collections.emptyList());
        assertEquals("ERROR", response.getStatus());
        assertTrue(response.getTickets().isEmpty());
        verifyNoInteractions(repository);
    }

    @Test void bothAlgorithmsAndRecommendationsUseTheProvidedHistory() {
        for (String category : List.of("POWER", "MEGA")) {
            List<LotteryNumber> draws = history(category, 0);
            PredictionResponseDto response = service.analyzeAndPredict(category, "bayesian_graph", "deep_stacking", draws);
            PredictionResponseDto secondary = service.analyzeAndPredict(category, "deep_stacking", "bayesian_graph", draws);
            assertEquals("SUCCESS", response.getStatus());
            assertEquals(secondary.getPrimaryAlgorithmResult().get("numbers"), response.getSecondaryAlgorithmResult().get("numbers"));
            assertNotNull(response.getCombinedAlgorithmResult());
            for (Map<String, Object> card : List.of(response.getPrimaryAlgorithmResult(), response.getSecondaryAlgorithmResult(), response.getCombinedAlgorithmResult())) {
                List<?> numbers = (List<?>) card.get("numbers");
                assertEquals(6, numbers.size());
                assertEquals(6, new HashSet<>(numbers).size());
                assertNull(card.get("accuracyScore"));
                assertFalse(numbers.contains(card.get("specialNumber")));
            }
            assertEquals("2026-09-15", response.getBaseDrawDate());
            Map<?, ?> reconciliation = (Map<?, ?>) response.getRecommendations().get("megaDrawReconciliation");
            assertEquals(draws.get(14).getNumbers(), reconciliation.get("officialWinningNumbers"));
            assertNull(reconciliation.get("upgradedAiCoverage"));
            Collections.reverse(draws);
            assertEquals(response.getPrimaryAlgorithmResult().get("numbers"),
                    service.analyzeAndPredict(category, "bayesian_graph", "deep_stacking", draws).getPrimaryAlgorithmResult().get("numbers"));
            assertNotEquals(response.getPrimaryAlgorithmResult().get("numbers"),
                    service.analyzeAndPredict(category, "bayesian_graph", history(category, 4)).getPrimaryAlgorithmResult().get("numbers"));
        }
        verifyNoInteractions(repository);
    }

    @Test void repeatedAndInvalidDrawsDoNotInflateSampleSize() {
        List<LotteryNumber> draws = history("MEGA", 0);
        draws.add(draws.get(0));
        draws.add(new LotteryNumber(LocalDate.of(2026, 10, 1), "MEGA", List.of(1, 1, 2, 3, 4, 99), null, null));
        assertEquals(15, service.analyzeAndPredict("MEGA", "bayesian_graph", draws).getTotalDrawsAnalyzed());
    }

    @Test void rankFusionIsSymmetricAndShiftedTicketsRemainValid() {
        List<Integer> a = List.of(1, 2, 3, 4, 5, 6, 7), b = List.of(7, 6, 5, 4, 3, 2, 1);
        assertEquals(PredictionSupport.fuse(a, b, 6), PredictionSupport.fuse(b, a, 6));
        assertEquals(6, PredictionSupport.distinctTicket(List.of(1, 1, 45, 45), a, 45).size());
        assertFalse(a.contains(PredictionSupport.specialFor(1, a, List.of(8, 9))));
    }

    @Test void parametersNeverFallBackToAnUnrelatedGame() {
        service.getLatestHyperparameter("MEGA");
        verify(parameters).findFirstByCategoryOrderByCreatedAtDesc("MEGA");
        verify(parameters).findFirstByCategoryOrderByCreatedAtDesc("ALL");
        verify(parameters, never()).findFirstByOrderByCreatedAtDesc();
    }

    @Test void targetDatesRespectVietnamCutoffAndFutureStoredDraws() {
        assertEquals(LocalDate.of(2026, 10, 25), PredictionSupport.nextDraw(false,
                ZonedDateTime.parse("2026-10-25T10:59:59Z"), null));
        assertEquals(LocalDate.of(2026, 10, 28), PredictionSupport.nextDraw(false,
                ZonedDateTime.parse("2026-10-25T11:00:00Z"), null));
        assertEquals(LocalDate.of(2026, 11, 12), PredictionSupport.nextDraw(true,
                ZonedDateTime.parse("2026-10-25T10:00:00Z"), LocalDate.of(2026, 11, 10)));
    }

    @Test void earliestBacktestDrawDoesNotTrainOnFutureResults() {
        List<LotteryNumber> draws = history("MEGA", 0);
        when(repository.findByCategoryOrderByDrawDateDescCreatedAtDesc("MEGA")).thenReturn(draws);
        Map<String, Object> result = service.reconcileDraws("MEGA", "bayesian_graph", 1, "2026-09-01");
        List<?> evaluated = (List<?>) result.get("draws");
        assertFalse(evaluated.isEmpty());
        Map<?, ?> firstDraw = (Map<?, ?>) evaluated.get(0);
        Map<?, ?> prediction = (Map<?, ?>) firstDraw.get("aiPrediction");
        assertEquals(Collections.emptyList(), prediction.get("predicted6Numbers"));
        assertEquals(Collections.emptyList(), prediction.get("top10Numbers"));
    }

    @Test void officialDrawFeaturesAreReproducible() {
        List<LotteryNumber> draws = history("MEGA", 0);
        Collections.reverse(draws);
        when(repository.findByCategoryOrderByDrawDateDescCreatedAtDesc("MEGA")).thenReturn(draws);
        Map<String, Object> first = service.analyzeOfficialDraw("MEGA", null, "bayesian_graph");
        Map<String, Object> second = service.analyzeOfficialDraw("MEGA", null, "bayesian_graph");
        List<?> a = (List<?>) first.get("selectionReasons"), b = (List<?>) second.get("selectionReasons");
        for (int i = 0; i < a.size(); i++) {
            var x = (com.example.analyzeproject.dto.NumberSelectionReasonDto) a.get(i);
            var y = (com.example.analyzeproject.dto.NumberSelectionReasonDto) b.get(i);
            assertEquals(x.getMarkov(), y.getMarkov());
            assertEquals(x.getPoisson(), y.getPoisson());
            assertEquals(x.getCompanion(), y.getCompanion());
        }
    }
}
