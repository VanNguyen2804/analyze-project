package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.domain.service.*;
import com.example.analyzeproject.application.dto.WorkspaceRequests.*;
import com.example.analyzeproject.application.dto.PredictionResponseDto;
import com.example.analyzeproject.application.service.WorkspaceService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/prediction-workspace")
public class WorkspaceController {
    private final WorkspaceService workspace;
    public WorkspaceController(WorkspaceService workspace) { this.workspace=workspace; }
    @PostMapping("/suggestions") public PredictionResponseDto create(@RequestBody Create request) { return workspace.create(request); }
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
