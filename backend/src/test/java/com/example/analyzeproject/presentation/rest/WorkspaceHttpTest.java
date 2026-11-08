package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.application.dto.WorkspaceRequests.Commit;
import com.example.analyzeproject.application.exception.ConflictException;
import com.example.analyzeproject.application.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkspaceHttpTest {
    @Test void applicationConflictRetainsHttp409AndMessage() throws Exception {
        var service = mock(WorkspaceService.class);
        when(service.commit(any(Commit.class))).thenThrow(new ConflictException("Lịch sử vừa đổi."));
        var http = MockMvcBuilders.standaloneSetup(new WorkspaceController(service, org.mockito.Mockito.mock(com.example.analyzeproject.application.service.PageJobService.class))).build();
        http.perform(post("/api/prediction-workspace/commit").contentType("application/json")
                .content("{\"category\":\"MEGA\",\"targetDate\":\"2026-11-04\",\"expectedRevision\":0,\"requestKey\":\"retry\",\"tickets\":[],\"labCount\":5}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Lịch sử vừa đổi."));
    }
}
