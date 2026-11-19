package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.PageJobStore.Work;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.Map;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OfficialResultLabTaskTest {
    @Test void verifiesSourceBeforeUpdatingLab() {
        var lab=mock(AiLabService.class);
        @SuppressWarnings("unchecked") Consumer<Map<String,Object>> sync=mock(Consumer.class);
        var tasks=new PageJobTasks(null,lab,null,Clock.systemUTC(),sync);
        var input=Map.<String,Object>of("category","POWER","drawDate","2026-11-17");
        tasks.execute(new Work("id","LAB_RUN",input,"token"));
        var order=inOrder(sync,lab);
        order.verify(sync).accept(input);
        order.verify(lab).run();
    }
    @Test void sourceFailureDoesNotTrainOnUnverifiedResult() {
        var lab=mock(AiLabService.class);
        var tasks=new PageJobTasks(null,lab,null,Clock.systemUTC(),input -> {throw new IllegalStateException("Source conflict");});
        assertThrows(IllegalStateException.class,()->tasks.execute(new Work("id","LAB_RUN",Map.of("drawDate","2026-11-17"),"token")));
        verifyNoInteractions(lab);
    }
}
