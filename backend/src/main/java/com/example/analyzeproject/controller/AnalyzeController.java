package com.example.analyzeproject.controller;

import com.example.analyzeproject.dto.PredictionResponseDto;
import com.example.analyzeproject.dto.TicketCheckRequestDto;
import com.example.analyzeproject.dto.TicketCheckResponseDto;
import com.example.analyzeproject.dto.DrawRecordDto;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.model.UserTicket;
import com.example.analyzeproject.model.AlgorithmHyperparameter;
import com.example.analyzeproject.model.LotteryDeviationVariable;
import com.example.analyzeproject.service.AnalyzeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
@CrossOrigin(origins = "*")
public class AnalyzeController {

    private final AnalyzeService analyzeService;
    private final com.example.analyzeproject.service.ForecastService forecasts;
    private final com.example.analyzeproject.service.DrawAnalysisService drawAnalysis;

    @Autowired
    public AnalyzeController(AnalyzeService analyzeService, com.example.analyzeproject.service.ForecastService forecasts,
                             com.example.analyzeproject.service.DrawAnalysisService drawAnalysis) {
        this.analyzeService = analyzeService;
        this.forecasts = forecasts;
        this.drawAnalysis = drawAnalysis;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }

    @GetMapping("/algorithms")
    public List<Map<String, Object>> algorithms() {
        return analyzeService.getAlgorithmCatalog();
    }

    @GetMapping("/relationship-learning")
    public ResponseEntity<?> relationshipLearning(@RequestParam(defaultValue = "POWER") String category,
            @RequestParam(required = false) String date) {
        Map<String, Object> result = analyzeService.getRelationshipLearning(category, date);
        return ResponseEntity.status("DRAW_NOT_FOUND".equals(result.get("status")) ? 404 : 200).body(result);
    }

    /**
     * ĐỐI SOÁT & KIỂM THỬ CÁC KỲ QUAY TRƯỚC (WALK-FORWARD BACKTESTING)
     * Endpoint: /api/analyze/reconcile-5-draws HOẶC /analyze/reconcile-5-draws
     * Hỗ trợ các tham số: category (MEGA|POWER), algorithm, limit (5, 10, 15, 20...), date (tùy chọn)
     */
    @GetMapping({"/reconcile-5-draws", "/reconcile-draws"})
    public ResponseEntity<?> getReconcile5Draws(
            @RequestParam(value = "category", defaultValue = "MEGA") String category,
            @RequestParam(value = "algorithm", defaultValue = "deep_stacking") String algorithm,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @RequestParam(value = "date", required = false) String date) {
        try {
            Map<String, Object> result = analyzeService.reconcileDraws(category, algorithm, limit, date);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(500).body(Map.of("status", "ERROR", "message", msg, "errorType", e.getClass().getName()));
        }
    }
    
    @RequestMapping(value = "/predict", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<PredictionResponseDto> predictNumbers(
            @RequestParam(required = false, defaultValue = "MEGA") String category,
            @RequestParam(required = false, defaultValue = "temporal_learning") String algorithm,
            @RequestParam(required = false) String algorithm2) {
        PredictionResponseDto result = forecasts.predict(category, algorithm, algorithm2);
        return ResponseEntity.ok(result);
    }

    /**
     * PHÂN TÍCH CHÍNH XÁC 6 SỐ TRÚNG THƯỞNG CHO TRANG LATEST-DRAW-ANALYSIS
     * Nhận đủ 3 tham số: category, date, algorithm.
     */
    @GetMapping("/official-draw-analysis")
    public ResponseEntity<?> getOfficialDrawAnalysis(
            @RequestParam(value = "category", defaultValue = "MEGA") String category,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "algorithm", defaultValue = "temporal_learning") String algorithm) {
        var report=drawAnalysis.report(category,date,algorithm,null);
        var result=new java.util.LinkedHashMap<String,Object>();
        result.put("status",report.status()); result.put("message",report.message());
        result.put("drawDate",report.context().drawDate()); result.put("algorithmName",algorithm);
        result.put("numbers",report.draw()==null?null:report.draw().numbers());
        result.put("specialNumber",report.draw()==null?null:report.draw().specialNumber());
        result.put("sum",report.draw()==null?null:report.draw().numbers().stream().mapToInt(Integer::intValue).sum());
        if(report.draw()!=null) {
            long even=report.draw().numbers().stream().filter(n->n%2==0).count();
            result.put("oddEvenRatio",even+" Chẵn / "+(6-even)+" Lẻ");
        }
        result.put("selectionReasons",report.draw()==null?List.of():report.numberDetails().stream().filter(n->report.draw().numbers().contains(n.number())).toList());
        result.put("allNumberDetails",report.numberDetails().stream().collect(java.util.stream.Collectors.toMap(n->n.number(),n->n)));
        result.put("evidence",report.evidence()); result.put("scoreSemantics","internal_score_not_probability");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/latest-draw")
    public ResponseEntity<?> getLatestDraw(
            @RequestParam(value = "category", defaultValue = "MEGA") String category,
            @RequestParam(value = "date", required = false) String drawDate) {
        DrawRecordDto result = analyzeService.getLatestDraw(category, drawDate);
        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(404)
                .body(Map.of("status", "NOT_FOUND", "message", "Không tìm thấy dữ liệu " + category + " cho ngày " + drawDate));
        }
    }

    @GetMapping("/history")
    public List<DrawRecordDto> getHistory(@RequestParam(defaultValue = "MEGA") String category) {
        return analyzeService.getRecentDraws(category);
    }

    @PostMapping("/check-tickets")
    public TicketCheckResponseDto checkTickets(@RequestBody TicketCheckRequestDto request) {
        return analyzeService.checkMyTickets(request);
    }

    @PostMapping("/add-result")
    public String addOfficialResult(@RequestBody LotteryNumber newDraw) {
        analyzeService.addNewDrawResult(newDraw);
        return "Đã cập nhật kết quả mới vào hệ thống.";
    }

    @PutMapping("/update-result/{id}")
    public String updateResult(@PathVariable Long id, @RequestBody LotteryNumber updatedDraw) {
        analyzeService.updateDrawResult(id, updatedDraw);
        return "Đã chỉnh sửa ngày và dãy số thành công!";
    }

    @GetMapping("/user-history")
    public List<UserTicket> getUserHistory() {
        return analyzeService.getUserHistory();
    }

    @DeleteMapping("/user-history")
    public String clearUserHistory() {
        analyzeService.clearUserHistory();
        return "Đã xóa lịch sử dò vé cá nhân.";
    }

    @GetMapping("/hyperparameters")
    public List<AlgorithmHyperparameter> getHyperparameters(@RequestParam(required = false) String category) {
        return analyzeService.getAllHyperparameters(category);
    }

    @GetMapping("/hyperparameters/latest")
    public ResponseEntity<?> getLatestHyperparameters(@RequestParam(required = false) String category) {
        AlgorithmHyperparameter latest = analyzeService.getLatestHyperparameter(category);
        if (latest != null) {
            return ResponseEntity.ok(latest);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/hyperparameters")
    public ResponseEntity<AlgorithmHyperparameter> saveHyperparameters(@RequestBody AlgorithmHyperparameter hyperparameter) {
        AlgorithmHyperparameter saved = analyzeService.saveHyperparameter(hyperparameter);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/update-algorithm")
    public ResponseEntity<AlgorithmHyperparameter> updateAlgorithm(@RequestBody Map<String, String> request) {
        String json = request.getOrDefault("hyperparametersJson", "{}");
        String category = request.getOrDefault("category", "POWER");
        String drawDate = request.getOrDefault("drawDate", "");
        String readme = request.getOrDefault("readmeContent", request.getOrDefault("readme", ""));
        String note = request.getOrDefault("note", "Cập nhật thuật toán theo phân tích đối soát");
        AlgorithmHyperparameter saved = analyzeService.updateAlgorithm(json, category, drawDate, readme, note);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/hyperparameters/activate/{id}")
    public ResponseEntity<AlgorithmHyperparameter> activateHyperparameter(@PathVariable Long id) {
        AlgorithmHyperparameter activated = analyzeService.activateHyperparameter(id);
        return ResponseEntity.ok(activated);
    }

    /**
     * LẤY DANH SÁCH BIẾN SỐ (DEVIATION VARIABLES) TỪ DATABASE POSTGRESQL
     */
    @GetMapping("/deviation-variables")
    public ResponseEntity<List<LotteryDeviationVariable>> getDeviationVariables(
            @RequestParam(required = false) String category) {
        List<LotteryDeviationVariable> list = analyzeService.getDeviationVariables(category);
        return ResponseEntity.ok(list);
    }

    /**
     * LƯU BIẾN SỐ MỚI VÀO DATABASE POSTGRESQL
     */
    @PostMapping("/deviation-variables")
    public ResponseEntity<LotteryDeviationVariable> saveDeviationVariable(
            @RequestBody LotteryDeviationVariable variable) {
        LotteryDeviationVariable saved = analyzeService.saveDeviationVariable(variable);
        return ResponseEntity.ok(saved);
    }
}
