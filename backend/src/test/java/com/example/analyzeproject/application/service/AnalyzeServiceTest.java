package com.example.analyzeproject.application.service;
import com.example.analyzeproject.domain.service.*;

import com.example.analyzeproject.application.dto.PredictionResponseDto;
import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.domain.model.AlgorithmHyperparameter;
import com.example.analyzeproject.application.port.out.*;
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
            parameters, null);

    @Test void bayesPairParametersActuallyControlScoringAndVersionsMatchBothSlots() {
        var draws=history("MEGA",0);
        var off=new AlgorithmHyperparameter(); off.setHyperparametersJson("{\"coOccurrenceWeight\":0,\"pairGraphMinSupport\":1}");
        var on=new AlgorithmHyperparameter(); on.setHyperparametersJson("{\"coOccurrenceWeight\":1,\"pairGraphMinSupport\":1}");
        var unsupported=new AlgorithmHyperparameter(); unsupported.setHyperparametersJson("{\"coOccurrenceWeight\":1,\"pairGraphMinSupport\":1000}");
        var smoothed=new AlgorithmHyperparameter(); smoothed.setHyperparametersJson("{\"coOccurrenceWeight\":1,\"pairGraphPriorStrength\":1000,\"pairGraphMinSupport\":1}");
        var zero=service.analyzeAndPredictWithConfiguration("MEGA","bayesian_graph","temporal_learning",draws,off);
        var normal=service.analyzeAndPredictWithConfiguration("MEGA","bayesian_graph","temporal_learning",draws,on);
        var neutral=service.analyzeAndPredictWithConfiguration("MEGA","bayesian_graph","temporal_learning",draws,unsupported);
        var shrunk=service.analyzeAndPredictWithConfiguration("MEGA","bayesian_graph","temporal_learning",draws,smoothed);
        assertEquals(AnalyzeService.BAYES_VERSION,normal.getModelVersion());
        assertEquals(TemporalRelationshipModel.VERSION,normal.getSecondaryAlgorithmResult().get("modelVersion"));
        var zeroDetails=byNumber(zero.getPrimaryAlgorithmResult());
        var details=byNumber(normal.getPrimaryAlgorithmResult());
        var neutralDetails=byNumber(neutral.getPrimaryAlgorithmResult());
        var shrunkDetails=byNumber(shrunk.getPrimaryAlgorithmResult());
        assertTrue(details.keySet().stream().anyMatch(n->!details.get(n).get("score").equals(zeroDetails.get(n).get("score"))));
        for(int n:details.keySet()) {
            assertEquals(zeroDetails.get(n).get("score"),neutralDetails.get(n).get("score"));
            Map<?,?> feature=(Map<?,?>)details.get(n).get("features"), smaller=(Map<?,?>)shrunkDetails.get(n).get("features");
            assertTrue(Math.abs(((Number)smaller.get("pairGraph")).doubleValue())<=Math.abs(((Number)feature.get("pairGraph")).doubleValue()));
        }
    }

    @Test void stackUsesGapSafeTransitionsInBothScoringSlotsAndFreezesDiagnostics() {
        var input=List.of(TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,1),"POWER",List.of(1,2,3,4,5,6),55,null)),
                TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,6),"POWER",List.of(20,21,22,23,24,25),55,null)));
        var response=service.analyzeAndPredictWithConfiguration("POWER","deep_stacking","deep_stacking",input,null);
        for(var slot:List.of(response.getPrimaryAlgorithmResult(),response.getSecondaryAlgorithmResult())) {
            assertEquals(AnalyzeService.STACK_VERSION,slot.get("modelVersion"));
            var diagnostics=(Map<?,?>)slot.get("scoringDiagnostics");
            assertEquals(0,diagnostics.get("learnedTransitions")); assertEquals(1,diagnostics.get("skippedGapTransitions"));
            for(var detail:byNumber(slot).values()) assertEquals(0.0,((Map<?,?>)detail.get("features")).get("markov2"));
            assertEquals(diagnostics,PredictionExplanation.fromResult(slot).get("scoringDiagnostics"));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Integer,Map<String,Object>> byNumber(Map<String,Object> result) {
        var values=new HashMap<Integer,Map<String,Object>>();
        for(var detail:(List<Map<String,Object>>)result.get("allDetails")) values.put(((Number)detail.get("number")).intValue(),detail);
        return values;
    }

    private List<LotteryNumber> history(String category, int offset) {
        List<LotteryNumber> result = new ArrayList<>();
        int max = category.equals("POWER") ? 55 : 45;
        for (int i = 0; i < 15; i++) {
            List<Integer> numbers = new ArrayList<>();
            for (int j = 0; j < 6; j++) numbers.add((i * 3 + j * 7 + offset) % max + 1);
            result.add(new LotteryNumber(LocalDate.of(2026, 9, i + 1), category, numbers, null, null));
        }
        result.forEach(TestDraws::verified);
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
            assertEquals(response.getNumbers().subList(0, 6), response.getTickets().get(0));
            @SuppressWarnings("unchecked")
            var experimental = (List<List<Integer>>) response.getRecommendations().get("diversifiedTickets");
            assertEquals(25, experimental.size());
            for (int i = 0; i < experimental.size(); i++)
                for (int j = 0; j < i; j++) assertTrue(TicketPortfolio.overlap(experimental.get(i), experimental.get(j)) <= 3);
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
        assertNull(((Map<?,?>)result.get("overallSummary")).get("ticketHitRatePercent"));
        assertNotNull(result.get("backtestComparison"));
        List<?> evaluated = (List<?>) result.get("draws");
        assertFalse(evaluated.isEmpty());
        Map<?, ?> firstDraw = (Map<?, ?>) evaluated.get(0);
        Map<?, ?> prediction = (Map<?, ?>) firstDraw.get("aiPrediction");
        assertEquals(Collections.emptyList(), prediction.get("predicted6Numbers"));
        assertEquals(Collections.emptyList(), prediction.get("top10Numbers"));
    }

    @Test void frozenExplanationContainsEveryRankWithoutChangingSelectedTicket() {
        var response = service.analyzeAndPredict("MEGA", "bayesian_graph", history("MEGA", 0));
        var card = response.getPrimaryAlgorithmResult();
        var explanation = PredictionExplanation.fromResult(card);
        assertEquals(45, ((List<?>)explanation.get("ranking")).size());
        assertEquals(45, ((List<?>)explanation.get("details")).size());
        assertFalse(((Map<?,?>)((Map<?,?>)((List<?>)explanation.get("details")).get(0)).get("features")).isEmpty());
        assertEquals(card.get("numbers"), ((List<Integer>)explanation.get("ranking")).stream().limit(6).sorted().toList());
        assertEquals(explanation, PredictionExplanation.fromResult(service.analyzeAndPredict("MEGA", "bayesian_graph", history("MEGA", 0)).getPrimaryAlgorithmResult()));
    }
}
