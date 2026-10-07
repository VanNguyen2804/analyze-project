package com.example.analyzeproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lottery_deviation_variables")
public class LotteryDeviationVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category; // "POWER" or "MEGA"

    @Column(name = "base_draw_date", nullable = false)
    private String baseDrawDate; // Ngày kỳ trước dùng để dự đoán

    @Column(name = "target_draw_date", nullable = false)
    private String targetDrawDate; // Ngày kỳ mở thưởng

    @Column(name = "ai_predicted_number", nullable = false)
    private Integer aiPredictedNumber; // Con số do AI đề xuất

    @Column(name = "actual_number", nullable = false)
    private Integer actualNumber; // Con số thực tế trả ra

    @Column(name = "variable_delta", nullable = false)
    private Integer variableDelta; // Biến số delta = actual - aiPredicted

    @Column(name = "variable_type", nullable = false)
    private String variableType; // NEIGHBOR_DRIFT, DECADE_SHIFT, RESONANCE_LEAP, SPECIAL_MIGRATION, REPEAT_INERTIA

    @Column(name = "pattern_name")
    private String patternName; // Tên mẫu biến số

    @Column(name = "probability_shift")
    private Double probabilityShift; // Tỷ lệ biến thiên

    @Column(name = "transformation_rule", columnDefinition = "TEXT")
    private String transformationRule; // Quy tắc chuyển đổi: AI (X) + Delta = Số thực tế (Y)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String note;

    public LotteryDeviationVariable() {
        this.createdAt = LocalDateTime.now();
    }

    public LotteryDeviationVariable(String category, String baseDrawDate, String targetDrawDate,
                                    Integer aiPredictedNumber, Integer actualNumber,
                                    Integer variableDelta, String variableType,
                                    String patternName, Double probabilityShift,
                                    String transformationRule, String note) {
        this.category = category;
        this.baseDrawDate = baseDrawDate;
        this.targetDrawDate = targetDrawDate;
        this.aiPredictedNumber = aiPredictedNumber;
        this.actualNumber = actualNumber;
        this.variableDelta = variableDelta;
        this.variableType = variableType;
        this.patternName = patternName;
        this.probabilityShift = probabilityShift;
        this.transformationRule = transformationRule;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getBaseDrawDate() { return baseDrawDate; }
    public void setBaseDrawDate(String baseDrawDate) { this.baseDrawDate = baseDrawDate; }

    public String getTargetDrawDate() { return targetDrawDate; }
    public void setTargetDrawDate(String targetDrawDate) { this.targetDrawDate = targetDrawDate; }

    public Integer getAiPredictedNumber() { return aiPredictedNumber; }
    public void setAiPredictedNumber(Integer aiPredictedNumber) { this.aiPredictedNumber = aiPredictedNumber; }

    public Integer getActualNumber() { return actualNumber; }
    public void setActualNumber(Integer actualNumber) { this.actualNumber = actualNumber; }

    public Integer getVariableDelta() { return variableDelta; }
    public void setVariableDelta(Integer variableDelta) { this.variableDelta = variableDelta; }

    public String getVariableType() { return variableType; }
    public void setVariableType(String variableType) { this.variableType = variableType; }

    public String getPatternName() { return patternName; }
    public void setPatternName(String patternName) { this.patternName = patternName; }

    public Double getProbabilityShift() { return probabilityShift; }
    public void setProbabilityShift(Double probabilityShift) { this.probabilityShift = probabilityShift; }

    public String getTransformationRule() { return transformationRule; }
    public void setTransformationRule(String transformationRule) { this.transformationRule = transformationRule; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
