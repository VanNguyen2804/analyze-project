package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.dto.AiLabReadView;
import com.example.analyzeproject.application.port.out.*;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.example.analyzeproject.domain.service.stock.StockLearningEngine;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PagePerformanceTest {
    @Test void pageSnapshotCacheReturnsBeforeProviderIoAndRefreshesAfterItsDeadline() {
        var now=new AtomicReference<>(Instant.parse("2026-11-07T01:00:00Z"));
        var clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}};
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var service=new StockService((symbol,mode)->{calls.incrementAndGet();return new Feed(StockPerformanceReportTest.candles(100),now.get(),"TEST","VND","RAW",true,List.of());},clock,new StockLearningEngine(.6,.6,.004));
        var first=service.analyzeCached("FPT",Mode.SWING);assertEquals(2,calls.get());
        assertSame(first,service.analyzeCached("FPT",Mode.SWING));assertEquals(2,calls.get());
        now.set(now.get().plusSeconds(61));service.analyzeCached("FPT",Mode.SWING);assertEquals(4,calls.get());
    }
    @Test void capacityRejectionIsRetryableAndHealthDoesNotDependOnFeeds() throws Exception {
        var service=mock(AiLabService.class);when(service.overview("MEGA",0)).thenThrow(new com.example.analyzeproject.application.exception.ServiceBusyException("busy"));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.example.analyzeproject.presentation.rest.AiLabController(service,mock(PageJobService.class)),new com.example.analyzeproject.presentation.rest.HealthController())
                .setControllerAdvice(new com.example.analyzeproject.presentation.rest.PageCapacityAdvice()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ai-lab"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After","2"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("SERVICE_BUSY"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/health"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(service,times(1)).overview("MEGA",0);verifyNoMoreInteractions(service);
    }
    @Test void repeatedStockRefreshesReuseTrainingButChangedClosedBarsTrainAgain() {
        var now=new AtomicReference<>(Instant.parse("2026-11-07T01:00:00Z"));
        var clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}};
        var bars=new AtomicReference<>(StockPerformanceReportTest.candles(350));
        var engine=spy(new StockLearningEngine(.6,.6,.004));
        var service=new StockService((symbol,mode)->new Feed(bars.get(),now.get(),"TEST","VND","RAW",true,List.of()),clock,engine);
        var first=service.analyze("FPT",Mode.SWING);now.set(now.get().plusSeconds(61));
        var refreshed=service.analyze("FPT",Mode.SWING);
        assertNotEquals(first.snapshotId(),refreshed.snapshotId());assertSame(first.analysis().learning(),refreshed.analysis().learning());
        verify(engine,times(1)).analyze(anyList(),eq(Mode.SWING));
        var changed=new ArrayList<>(bars.get());var last=changed.get(changed.size()-1);
        changed.set(changed.size()-1,new Candle(last.time(),last.open(),Math.max(last.high(),last.close()*1.2),last.low(),last.close()*1.2,last.volume(),true));bars.set(changed);
        service.analyze("FPT",Mode.SWING);verify(engine,times(2)).analyze(anyList(),eq(Mode.SWING));
        now.set(now.get().plus(Duration.ofDays(10)));
        var stale=service.analyze("FPT",Mode.SWING);assertEquals("STALE",stale.freshness());assertEquals("UNAVAILABLE",stale.analysis().signal());
        verify(engine,times(2)).analyze(anyList(),eq(Mode.SWING));
    }
    @Test void aBlockedStockSourceDoesNotBlockAnotherSymbolOrExistingPosition() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var clock=Clock.fixed(Instant.parse("2026-11-07T01:00:00Z"),ZoneOffset.UTC);
        StockMarketDataPort feed=(symbol,mode)->{
            if(symbol.equals("MBB")){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
            return new Feed(StockPerformanceReportTest.candles(100),clock.instant(),"TEST","VND","RAW",true,List.of());
        };
        var service=new StockService(feed,clock,new StockLearningEngine(.6,.6,.004));var snapshot=service.analyze("FPT",Mode.SWING);
        var pool=Executors.newFixedThreadPool(3);
        try {
            var blocked=pool.submit(()->service.analyze("MBB",Mode.SWING));assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertEquals("VIC",pool.submit(()->service.analyze("VIC",Mode.SWING)).get(2,TimeUnit.SECONDS).symbol());
            var input=new PositionInput(null,100.0,100.0,null,null,10.0,100.0);
            assertNotNull(pool.submit(()->service.position("FPT",snapshot.snapshotId(),input)).get(2,TimeUnit.SECONDS).grossPnl());
            release.countDown();blocked.get(2,TimeUnit.SECONDS);
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void concurrentLabPagesShareACompactReadAndNeverStartLearningOrReconciliation() throws Exception {
        var store=mock(AiLabStore.class);var draws=mock(LotteryNumberRepository.class);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(store.readOverview("MEGA")).thenAnswer(call->{entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return new AiLabReadView(List.of(),List.of());});
        when(store.readOverview("POWER")).thenReturn(new AiLabReadView(List.of(),List.of()));
        var lab=new AiLabService(store,draws,true);var pool=Executors.newFixedThreadPool(5);
        try {
            var first=pool.submit(()->lab.overview("MEGA",0));assertTrue(entered.await(2,TimeUnit.SECONDS));
            var second=pool.submit(()->lab.overview("MEGA",1));
            var rejected=assertThrows(ExecutionException.class,()->second.get(2,TimeUnit.SECONDS));
            assertInstanceOf(com.example.analyzeproject.application.exception.ServiceBusyException.class,rejected.getCause());
            assertEquals(0,pool.submit(()->lab.overview("POWER",0)).get(2,TimeUnit.SECONDS).get("totalRows"));
            release.countDown();first.get(2,TimeUnit.SECONDS);lab.overview("MEGA",1);lab.overview("MEGA",2);
            verify(store,times(1)).readOverview("MEGA");verify(store,never()).ledger();verify(store,never()).snapshots();
            verify(store,never()).captureBatch(anyList());verify(store,never()).reconcile(anyList(),any());verifyNoInteractions(draws);
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void failedReadsAreNotCachedAndInvalidRequestsNeverQueryTheStore() {
        var store=mock(AiLabStore.class);var lab=new AiLabService(store,mock(LotteryNumberRepository.class),true);
        when(store.readOverview("MEGA")).thenThrow(new IllegalStateException("offline")).thenReturn(new AiLabReadView(List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->lab.overview("BAD",0));
        assertThrows(IllegalStateException.class,()->lab.overview("MEGA",0));assertEquals(0,lab.overview("MEGA",0).get("totalRows"));
        verify(store,times(2)).readOverview("MEGA");
    }
}
