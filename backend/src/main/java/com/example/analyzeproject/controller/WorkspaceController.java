package com.example.analyzeproject.controller;
import com.example.analyzeproject.dto.WorkspaceRequests.*;
import com.example.analyzeproject.dto.PredictionResponseDto;
import com.example.analyzeproject.service.WorkspaceService;
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
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class) public ResponseEntity<?> conflict(org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",java.util.Objects.toString(e.getReason(),"Không hoàn tất thao tác.")));
    }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));
    }
}
