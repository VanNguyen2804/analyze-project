package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.PredictionResponseDto;
import com.example.analyzeproject.model.AlgorithmHyperparameter;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ForecastServiceTest {
    @Test void predictionAndSnapshotUseTheSameCapturedConfigurationAndFilteredInput() {
        var analyze = mock(AnalyzeService.class); var repository = mock(LotteryNumberRepository.class); var store = mock(ForecastStore.class);
        var active = new AlgorithmHyperparameter(); active.setVersion("locked-config"); active.setHyperparametersJson("{\"temporalLearningRate\":0.25}");
        when(analyze.getLatestHyperparameter("MEGA")).thenReturn(active);
        var now = Instant.now(); var date = ForecastProtocol.expected("MEGA",now);
        List<LotteryNumber> rows = new ArrayList<>();
        for(int i=0;i<5;i++) {
            rows.add(TestDraws.verified(new LotteryNumber(date,"MEGA",List.of(1,2,3,4,5,6),null,null)));
            do { date = date.minusDays(1); } while(!Set.of(3,5,7).contains(date.getDayOfWeek().getValue()));
        }
        rows.add(TestDraws.verified(new LotteryNumber(ForecastProtocol.target("MEGA",now),"MEGA",List.of(10,11,12,13,14,15),null,null)));
        when(repository.findByCategoryOrderByDrawDateDescCreatedAtDesc("MEGA")).thenReturn(rows);
        var response = new PredictionResponseDto(); response.setAlgorithm("temporal_learning"); response.setModelVersion("temporal-links-v2");
        response.setTargetDrawDate(ForecastProtocol.target("MEGA",now).toString()); response.setPrimaryAlgorithmResult(Map.of("numbers",List.of(1,2,3,4,5,6)));
        when(analyze.analyzeAndPredictWithConfiguration(eq("MEGA"),eq("temporal_learning"),isNull(),anyList(),same(active))).thenAnswer(call -> {
            assertEquals(5,((List<?>)call.getArgument(3)).size()); return response;
        });
        when(store.capture(anyMap())).thenAnswer(call -> {
            Map<String,Object> snapshot = call.getArgument(0);
            assertEquals("locked-config",((Map<?,?>)snapshot.get("configuration")).get("version"));
            assertTrue(ForecastProtocol.isProspective(snapshot)); return snapshot;
        });
        var result = new ForecastService(analyze,repository,store,true).predict("MEGA","temporal_learning",null);
        assertEquals("SAVED",result.getForecastSnapshot().get("status"));
        verify(analyze,times(1)).getLatestHyperparameter("MEGA");
        verify(store).capture(anyMap());
    }
}
