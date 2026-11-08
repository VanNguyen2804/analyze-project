package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.PageJobStore;
import com.example.analyzeproject.application.exception.ServiceBusyException;
import com.example.analyzeproject.infrastructure.scheduling.LocalPageJobStore;
import com.example.analyzeproject.infrastructure.scheduling.PageJobRunner;
import com.example.analyzeproject.presentation.rest.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PageJobTest {
    @Test void queuedPredictionDoesNotSilentlyMoveToAnotherDrawAfterCutoff() {
        Instant now=Instant.parse("2026-11-08T11:30:00Z");
        String oldTarget=ForecastProtocol.target("MEGA",now.minusSeconds(3600)).toString();
        assertNotEquals(oldTarget,ForecastProtocol.target("MEGA",now).toString());
        var workspace=mock(WorkspaceService.class);
        var tasks=new PageJobTasks(mock(StockService.class),mock(AiLabService.class),workspace,Clock.fixed(now,ZoneOffset.UTC));
        var work=new PageJobStore.Work("unused","ANALYZE",Map.of("category","MEGA","_target",oldTarget),"token");
        assertThrows(IllegalStateException.class,()->tasks.execute(work));verifyNoInteractions(workspace);
    }
    @Test void runningAHeavyTaskKeepsStatusReadsAvailableAndDoesNotExecuteItTwice() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var store=spy(new LocalPageJobStore());var tasks=mock(PageJobTasks.class);Instant now=Instant.now();
        var job=store.enqueue("slow","STOCK_ANALYSIS",Map.of(),30,now);
        when(tasks.execute(any())).thenAnswer(call->{entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return Map.of("done",true);});
        doAnswer(call->{call.callRealMethod();finished.countDown();return null;}).when(store).complete(any(),any(),any());
        try(var runner=new PageJobRunner(store,tasks,Clock.systemUTC(),1,"test-page-worker")) {
            assertTrue(entered.await(3,TimeUnit.SECONDS));assertEquals("RUNNING",store.find(job.id()).status());
            assertEquals(job.id(),store.enqueue("slow","STOCK_ANALYSIS",Map.of(),30,now).id());
            release.countDown();assertTrue(finished.await(3,TimeUnit.SECONDS));
            assertEquals("SUCCEEDED",store.find(job.id()).status());verify(tasks,times(1)).execute(any());
        } finally {release.countDown();}
    }
    @Test void duplicateRequestsShareWorkAndExpiryDoesNotReuseFailedOrOldResults() {
        var store=new LocalPageJobStore();Instant now=Instant.parse("2026-11-08T10:00:00Z");
        var first=store.enqueue("FPT:SWING","STOCK_ANALYSIS",Map.of(),30,now);
        assertEquals(first.id(),store.enqueue("FPT:SWING","STOCK_ANALYSIS",Map.of(),30,now).id());
        var work=store.claim(now);assertNotNull(work);assertNull(store.claim(now));
        store.complete(work,Map.of("snapshotId","original"),now);
        assertEquals(first.id(),store.enqueue("FPT:SWING","STOCK_ANALYSIS",Map.of(),30,now.plusSeconds(29)).id());
        assertNotEquals(first.id(),store.enqueue("FPT:SWING","STOCK_ANALYSIS",Map.of(),30,now.plusSeconds(31)).id());
        var retry=store.claim(now.plusSeconds(31));store.fail(retry,now.plusSeconds(32));
        store.complete(retry,"late result",now.plusSeconds(33));
        assertEquals("FAILED",store.find(retry.id()).status());
    }
    @Test void queueIsBoundedWithoutDroppingAnAcceptedJob() {
        var store=new LocalPageJobStore();Instant now=Instant.now();
        for(int i=0;i<16;i++)store.enqueue("key"+i,"STOCK_ANALYSIS",Map.of(),30,now);
        assertThrows(ServiceBusyException.class,()->store.enqueue("overflow","STOCK_ANALYSIS",Map.of(),30,now));
        assertNotNull(store.claim(now));
    }
    @Test void asyncHttpAcceptsAndPollsWithoutCallingTheFeedOnRequestThread() throws Exception {
        var stocks=mock(StockService.class);when(stocks.instruments()).thenReturn(List.of(Map.of("symbol","FPT")));
        var local=new LocalPageJobStore();var jobs=new PageJobService(mock(PageJobStore.class),local,Clock.systemUTC());
        var http=MockMvcBuilders.standaloneSetup(new StockController(stocks,jobs),new PageJobController(jobs)).build();
        http.perform(get("/api/stocks/FPT/analysis").param("async","true")).andExpect(status().isAccepted()).andExpect(header().string("Retry-After","2")).andExpect(jsonPath("$.status").value("QUEUED"));
        verify(stocks,never()).analyze(anyString(),any());
        var work=local.claim(Instant.now());local.complete(work,Map.of("snapshotId","done"),Instant.now());
        http.perform(get("/api/page-jobs/"+work.id())).andExpect(status().isOk()).andExpect(jsonPath("$.data.snapshotId").value("done"));
        http.perform(get("/api/page-jobs/s-00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
        http.perform(get("/api/stocks/INVALID/analysis").param("async","true")).andExpect(status().isBadRequest());
    }
}
