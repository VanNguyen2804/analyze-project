package com.example.analyzeproject.service;

import com.example.analyzeproject.controller.MegaPortfolioController;
import com.example.analyzeproject.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MegaPortfolioServiceTest {
    private final AnalyzeService analyze=mock(AnalyzeService.class);
    private final LotteryNumberRepository draws=mock(LotteryNumberRepository.class);
    private final MegaPortfolioStore store=mock(MegaPortfolioStore.class);
    private MegaPortfolioService service(boolean enabled) {
        when(store.find(any())).thenReturn(null);
        return new MegaPortfolioService(analyze,draws,store,enabled,Clock.fixed(MegaPortfolioProtocolTest.BEFORE,ZoneOffset.UTC));
    }
    @Test void firstCaptureUsesFrozenDefaultsAndRetryKeepsSavedBatch() {
        var history=MegaPortfolioProtocolTest.history();when(draws.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(history);
        when(analyze.analyzeAndPredictWithConfiguration("MEGA","temporal_learning",null,history,null)).thenReturn(MegaPortfolioProtocolTest.prediction());
        var service=service(true);assertEquals("SAVED",service.run().get("status"));
        verify(store).capture(argThat(f -> MegaPortfolioProtocol.valid(f) && f.get("category").equals("MEGA")));
        when(store.find(LocalDate.of(2026,10,25))).thenReturn(MegaPortfolioProtocolTest.snapshot());
        assertEquals("ALREADY_SAVED",service.run().get("status"));
        verify(analyze,times(1)).analyzeAndPredictWithConfiguration(anyString(),anyString(),isNull(),anyList(),isNull());
        verify(store,times(2)).reconcile(eq(history),any());
    }
    @Test void missingPriorDrawOrDisabledCaptureStillReconcilesOldBatches() {
        var stale=new ArrayList<>(MegaPortfolioProtocolTest.history().subList(0,9));
        stale.add(TestDraws.verified(new com.example.analyzeproject.model.LotteryNumber(LocalDate.of(2026,9,30),"MEGA",List.of(1,2,3,4,5,6),null,null)));
        when(draws.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(stale);
        assertEquals("NOT_SAVED",service(true).run().get("status"));
        assertEquals("DISABLED",service(false).run().get("status"));
        verifyNoInteractions(analyze);verify(store,never()).capture(anyMap());verify(store,times(2)).reconcile(eq(stale),any());
    }
    @Test void getOnlyReadsAndRoutesRequirePostForCapture() throws Exception {
        when(store.ledger()).thenReturn(ForecastProtocol.map("forecasts",List.of(),"settlements",List.of()));
        var http=MockMvcBuilders.standaloneSetup(new MegaPortfolioController(service(true))).build();
        http.perform(get("/api/mega-portfolio")).andExpect(status().isOk()).andExpect(jsonPath("$.current").isEmpty()).andExpect(jsonPath("$.totalRows").value(0));
        http.perform(get("/api/mega-portfolio").param("page","-1")).andExpect(status().isBadRequest());
        http.perform(get("/api/mega-portfolio/run")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(draws,analyze);verify(store,never()).capture(anyMap());verify(store,never()).reconcile(anyList(),any());
    }
    @Test void crossingCutoffDuringSelectionDoesNotSaveTheOldTarget() {
        var history=MegaPortfolioProtocolTest.history();when(draws.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(history);
        when(store.find(any())).thenReturn(null);
        when(analyze.analyzeAndPredictWithConfiguration("MEGA","temporal_learning",null,history,null)).thenReturn(MegaPortfolioProtocolTest.prediction());
        var clock=mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-25T10:59:58Z"),Instant.parse("2026-10-25T10:59:59Z"),Instant.parse("2026-10-25T11:00:00Z"));
        var service=new MegaPortfolioService(analyze,draws,store,true,clock);
        assertEquals("NOT_SAVED",service.run().get("status"));
        verify(store,never()).capture(anyMap());verify(store).reconcile(eq(history),any());
    }
    @Test @SuppressWarnings("unchecked") void captureExcludesSavedForecastAndLabTicketsForTheSameDraw() {
        var history=MegaPortfolioProtocolTest.history();when(draws.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(history);
        when(store.find(any())).thenReturn(null);
        when(analyze.analyzeAndPredictWithConfiguration("MEGA","temporal_learning",null,history,null)).thenReturn(MegaPortfolioProtocolTest.prediction());
        var source=mock(ForecastStore.class);var lab=mock(AiLabStore.class);
        var candidates=TicketPortfolio.select(java.util.stream.IntStream.rangeClosed(1,45).boxed().toList(),45,25);
        var frozen=ForecastProtocol.prepare(history,"MEGA","source","model","code",Map.of(),candidates.get(1),MegaPortfolioProtocolTest.BEFORE);
        var frozenLab=ForecastProtocol.prepare(history,"MEGA","lab","model","code",Map.of(),candidates.get(2),MegaPortfolioProtocolTest.BEFORE);
        when(source.forecasts()).thenReturn(List.of(frozen));when(lab.snapshots()).thenReturn(List.of(frozenLab));
        var service=new MegaPortfolioService(analyze,draws,store,true,Clock.fixed(MegaPortfolioProtocolTest.BEFORE,ZoneOffset.UTC),source,lab);
        assertEquals("SAVED",service.run().get("status"));
        verify(store).capture(argThat(f -> {
            var tickets=((Map<String,List<List<Integer>>>)f.get("arms")).get("portfolio");
            return !tickets.contains(candidates.get(1)) && !tickets.contains(candidates.get(2));
        }));
    }
}
