package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.domain.service.*;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.application.dto.NumberEntryRequest;
import com.example.analyzeproject.application.service.LotteryNumberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/numbers")
public class LotteryNumberController {

    private final LotteryNumberService service;

    private final com.example.analyzeproject.application.service.PageJobService jobs;

    @Autowired
    public LotteryNumberController(LotteryNumberService service, com.example.analyzeproject.application.service.PageJobService jobs) {
        this.service = service;
        this.jobs = jobs;
    }

    @GetMapping
    public ResponseEntity<List<LotteryNumber>> getAll(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(service.getAllNumbers(date, category));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody NumberEntryRequest request) {
        try {
            LotteryNumber saved = service.saveNumbers(request);
            return savedResponse(saved, HttpStatus.CREATED);
        } catch (IllegalArgumentException ex) {
            Map<String, String> error = new HashMap<>();
            error.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody NumberEntryRequest request) {
        try {
            LotteryNumber updated = service.updateNumbers(id, request);
            return savedResponse(updated, HttpStatus.OK);
        } catch (IllegalArgumentException ex) {
            Map<String, String> error = new HashMap<>();
            error.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    private ResponseEntity<LotteryNumber> savedResponse(LotteryNumber saved, HttpStatus status) {
        try {
            var job = jobs.submit("LAB_RUN", Map.of("category", saved.getCategory(),
                    "drawDate", saved.getDrawDate().toString(),
                    "resultFingerprint", com.example.analyzeproject.domain.service.DrawQuality.fingerprint(saved)));
            return ResponseEntity.status(status).header("X-AI-Lab-Job-Id", job.id())
                    .header("X-AI-Lab-Sync", job.status()).body(saved);
        } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "Official result saved but AI Lab could not be queued", error);
            return ResponseEntity.status(status).header("X-AI-Lab-Sync", "QUEUE_FAILED").body(saved);
        }
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        boolean deleted = service.deleteNumber(id);
        Map<String, Object> response = new HashMap<>();
        if (deleted) {
            response.put("success", true);
            response.put("message", "Đã xóa thành công khỏi hệ thống");
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Không tìm thấy bản ghi có ID: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }
}
