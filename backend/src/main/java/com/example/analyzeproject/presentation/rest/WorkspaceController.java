package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.domain.service.*;
import com.example.analyzeproject.application.dto.WorkspaceRequests.*;
import com.example.analyzeproject.application.dto.PredictionResponseDto;
import com.example.analyzeproject.application.service.WorkspaceService;
import com.example.analyzeproject.application.service.PageJobService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/prediction-workspace")
public class WorkspaceController {
    private final WorkspaceService workspace;
    private final PageJobService jobs;
    public WorkspaceController(WorkspaceService workspace,PageJobService jobs) { this.workspace=workspace;this.jobs=jobs; }
    @PostMapping("/suggestions") public Object create(@RequestBody Create request,@RequestParam(defaultValue="false") boolean async) {
        workspace.validate(request);
        return async?PageJobController.response(jobs.submit("ANALYZE",Map.of("category",com.example.analyzeproject.application.service.WorkspaceProtocol.category(request.category()),"algorithm",request.algorithm(),"algorithm2",request.algorithm2()))):workspace.create(request);
    }
    @PostMapping("/suggestions/{id}/combine") public Map<String,Object> combine(@PathVariable String id,@RequestBody Combine request) { return workspace.combine(id,request); }
    @GetMapping("/lab-suggestions") public Map<String,Object> lab(@RequestParam String category,@RequestParam String targetDate) { return workspace.labSuggestions(category,targetDate); }
    @PostMapping("/commit") public Map<String,Object> commit(@RequestBody Commit request) { return workspace.commit(request); }
    @GetMapping("/history") public Map<String,Object> history(@RequestParam String category) { return workspace.history(category); }
    @PostMapping("/reconcile") public Map<String,Object> reconcile() { return workspace.reconcile(); }
    @ExceptionHandler(com.example.analyzeproject.application.exception.ConflictException.class) public ResponseEntity<?> conflict(com.example.analyzeproject.application.exception.ConflictException e) {
        return ResponseEntity.status(409).body(Map.of("message",java.util.Objects.toString(e.getMessage(),"Không hoàn tất thao tác.")));
    }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));
    }
}
