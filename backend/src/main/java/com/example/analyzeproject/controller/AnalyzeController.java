package com.example.analyzeproject.controller;

import com.example.analyzeproject.dto.DrawRecordDto;
import com.example.analyzeproject.dto.PredictionResponseDto;
import com.example.analyzeproject.dto.TicketCheckRequestDto;
import com.example.analyzeproject.dto.TicketCheckResponseDto;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.service.AnalyzeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analyze")
@CrossOrigin(origins = "*")
public class AnalyzeController {

    private final AnalyzeService analyzeService;

    @Autowired
    public AnalyzeController(AnalyzeService analyzeService) {
        this.analyzeService = analyzeService;
    }

    @PostMapping("/check-tickets")
    public TicketCheckResponseDto checkTickets(@RequestBody TicketCheckRequestDto request) {
        return analyzeService.checkMyTickets(request);
    }

    @PostMapping("/add-result")
    public String addOfficialResult(@RequestBody LotteryNumber newDraw) {
        analyzeService.addNewDrawResult(newDraw);
        return "Đã cập nhật kết quả mới vào hệ thống. Thuật toán đã được hiệu chỉnh mốc thống kê.";
    }

    @GetMapping("/history")
    public List<DrawRecordDto> getHistory(@RequestParam(defaultValue = "MEGA") String category) {
        return analyzeService.getRecentDraws(category);
    }
    
    /**
     * Phân tích theo từng dãy số theo ngày cho từng category và đề xuất 6 số tối ưu.
     * @param category MEGA (1-45) hoặc POWER (1-55)
     */
    @GetMapping("/predict")
    public ResponseEntity<PredictionResponseDto> predictNumbers(
            @RequestParam(required = false, defaultValue = "MEGA") String category,
            @RequestParam(required = false, defaultValue = "xgboost") String algorithm) {
        PredictionResponseDto result = analyzeService.analyzeAndPredict(category, algorithm);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/update-result/{id}")
    public String updateResult(@PathVariable Long id, @RequestBody com.example.analyzeproject.model.LotteryNumber updatedDraw) {
        analyzeService.updateDrawResult(id, updatedDraw);
        return "Đã chỉnh sửa dãy số thành công!";
    }

    @GetMapping("/user-history")
    public List<com.example.analyzeproject.model.UserTicket> getUserHistory() {
        return analyzeService.getUserHistory();
    }

    @DeleteMapping("/user-history")
    public String clearUserHistory() {
        analyzeService.clearUserHistory();
        return "Đã xóa lịch sử dò vé cá nhân.";
    }
}
