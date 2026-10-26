package com.example.analyzeproject.service;

import com.example.analyzeproject.repository.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ForecastPerformanceTest {
    private final AnalyzeService analyze = mock(AnalyzeService.class);
    private final LotteryNumberRepository draws = mock(LotteryNumberRepository.class);
    private final ForecastStore store = mock(ForecastStore.class);
    private final ForecastService service = new ForecastService(analyze,draws,store,true);
    private Map<String,Object> emptyLedger() { return ForecastProtocol.map("forecasts",List.of(),"settlements",List.of()); }

    @Test void concurrentReadsShareOneLedgerFetchAndNeverReconcileOrLoadDraws() throws Exception {
        when(store.readLedger()).thenReturn(emptyLedger());
        var executor = Executors.newFixedThreadPool(8);
        try {
            var start = new CountDownLatch(1);
            List<Future<Map<String,Object>>> calls = new ArrayList<>();
            for(int i=0;i<8;i++) calls.add(executor.submit(() -> { start.await(); return service.overview("MEGA",0,50); }));
            start.countDown();
            for(var call : calls) assertEquals(0,call.get(5,TimeUnit.SECONDS).get("totalRows"));
            service.list(); service.evaluation("POWER");
            verify(store,times(1)).readLedger();
            verify(store,never()).reconcile(anyList(),any());
            verifyNoInteractions(draws,analyze);
        } finally { executor.shutdownNow(); }
    }

    @Test void summaryIsCategoryFilteredPagedAndDoesNotReturnHistoryOrConfiguration() {
        var first = ForecastProtocol.map("id","a","category","MEGA","targetDate","2026-10-28","numbers",List.of(1,2,3,4,5,6),"history",List.of("large private history"),"configuration",Map.of("secret","not needed"));
        var second = new LinkedHashMap<>(first); second.put("id","b"); second.put("targetDate","2026-10-30");
        var power = new LinkedHashMap<>(first); power.put("category","POWER"); power.put("id","p");
        var earlier = ForecastProtocol.map("forecastId","b","status","PENDING");
        var latest = ForecastProtocol.map("forecastId","b","status","SETTLED","matched",3);
        when(store.readLedger()).thenReturn(ForecastProtocol.map("forecasts",List.of(first,second,power),"settlements",List.of(earlier,latest)));
        var summary = service.overview("MEGA",0,1);
        assertEquals(2,summary.get("totalRows"));
        Map<?,?> row = (Map<?,?>)((List<?>)summary.get("rows")).get(0);
        assertEquals("b",row.get("id")); assertEquals(latest,row.get("settlement"));
        assertFalse(row.containsKey("history")); assertFalse(row.containsKey("configuration"));
        assertEquals("a",((Map<?,?>)((List<?>)service.overview("MEGA",1,1).get("rows")).get(0)).get("id"));
        assertThrows(IllegalArgumentException.class,()->service.overview("MEGA",-1,50));
        assertThrows(IllegalArgumentException.class,()->service.overview("MEGA",0,101));
    }

    @Test void explicitReconcileInvalidatesReadCache() {
        when(store.readLedger()).thenReturn(emptyLedger());
        when(draws.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(List.of());
        when(store.reconcile(anyList(),any())).thenReturn(emptyLedger());
        service.overview("MEGA",0,50); service.reconcile(); service.overview("MEGA",0,50);
        verify(store,times(2)).readLedger(); verify(store,times(1)).reconcile(anyList(),any());
    }

    @Test void overlappingMutationsReturnBusyInsteadOfWaitingForAnotherDatabaseLock() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(draws.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(List.of());
        when(store.reconcile(anyList(),any())).thenAnswer(call -> { entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); return emptyLedger(); });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(service::reconcile);
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertEquals("BUSY",service.reconcile().get("status"));
            assertEquals("BUSY",service.captureAll().get("status"));
            release.countDown(); first.get(5,TimeUnit.SECONDS);
            verify(store,times(1)).reconcile(anyList(),any());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
}
