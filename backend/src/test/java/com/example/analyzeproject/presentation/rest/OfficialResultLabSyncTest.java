package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.application.service.*;
import com.example.analyzeproject.application.dto.NumberEntryRequest;
import com.example.analyzeproject.application.port.out.PageJobStore.Job;
import com.example.analyzeproject.domain.model.LotteryNumber;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OfficialResultLabSyncTest {
    private final LotteryNumberService numbers = mock(LotteryNumberService.class);
    private final PageJobService jobs = mock(PageJobService.class);
    private final LotteryNumberController controller = new LotteryNumberController(numbers, jobs);
    private LotteryNumber draw() {
        return new LotteryNumber(LocalDate.of(2026,11,17), "POWER", List.of(1,2,3,4,5,6), 7, null);
    }
    @Test void savingAndEditingQueueLabOnlyAfterPersistence() {
        var request = new NumberEntryRequest();
        var saved = draw();
        when(numbers.saveNumbers(request)).thenReturn(saved);
        when(numbers.updateNumbers(1L, request)).thenReturn(saved);
        when(jobs.submit(eq("LAB_RUN"), anyMap())).thenReturn(new Job("l-test", "QUEUED", null, null, Instant.now()));
        assertEquals(201, controller.create(request).getStatusCode().value());
        assertEquals("l-test", controller.update(1L,request).getHeaders().getFirst("X-AI-Lab-Job-Id"));
        var order = inOrder(numbers,jobs);
        order.verify(numbers).saveNumbers(request);
        order.verify(jobs).submit(eq("LAB_RUN"), argThat(p -> p.get("drawDate").equals("2026-11-17") && p.get("category").equals("POWER")));
        order.verify(numbers).updateNumbers(1L,request);
        order.verify(jobs).submit(eq("LAB_RUN"), anyMap());
    }
    @Test void rejectedSaveDoesNotTriggerTraining() {
        when(numbers.saveNumbers(any())).thenThrow(new IllegalArgumentException("Invalid draw"));
        assertEquals(400,controller.create(new NumberEntryRequest()).getStatusCode().value());
        verifyNoInteractions(jobs);
    }
    @Test void queueFailureDoesNotReportPersistedResultAsFailed() {
        var saved=draw();
        when(numbers.saveNumbers(any())).thenReturn(saved);
        when(jobs.submit(anyString(),anyMap())).thenThrow(new IllegalStateException("Queue full"));
        var response=controller.create(new NumberEntryRequest());
        assertEquals(201,response.getStatusCode().value());
        assertSame(saved,response.getBody());
        assertEquals("QUEUE_FAILED",response.getHeaders().getFirst("X-AI-Lab-Sync"));
    }
}
