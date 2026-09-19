package com.example.analyzeproject.controller;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.model.NumberEntryRequest;
import com.example.analyzeproject.service.LotteryNumberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/numbers")
@CrossOrigin(origins = "*")
public class LotteryNumberController {

    private final LotteryNumberService service;

    @Autowired
    public LotteryNumberController(LotteryNumberService service) {
        this.service = service;
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
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException ex) {
            Map<String, String> error = new HashMap<>();
            error.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
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
