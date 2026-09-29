package com.example.analyzeproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "algorithm_hyperparameters")
public class AlgorithmHyperparameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String version; // e.g. "v1.1.0"

    @Column(name = "draw_date", nullable = false)
    private String drawDate; // e.g. "2026-09-28"

    @Column(nullable = false)
    private String category; // "POWER", "MEGA", or "ALL"

    private String model; // "XGBoost Multi-Factor Optimization"

    @Column(name = "hyperparameters_json", columnDefinition = "TEXT", nullable = false)
    private String hyperparametersJson;

    @Column(name = "readme_content", columnDefinition = "TEXT")
    private String readmeContent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String note;

    public AlgorithmHyperparameter() {
        this.createdAt = LocalDateTime.now();
    }

    public AlgorithmHyperparameter(String version, String drawDate, String category, String model, String hyperparametersJson, String note) {
        this.version = version;
        this.drawDate = drawDate;
        this.category = category != null ? category.toUpperCase() : "ALL";
        this.model = model;
        this.hyperparametersJson = hyperparametersJson;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    public AlgorithmHyperparameter(String version, String drawDate, String category, String model, String hyperparametersJson, String readmeContent, String note) {
        this.version = version;
        this.drawDate = drawDate;
        this.category = category != null ? category.toUpperCase() : "ALL";
        this.model = model;
        this.hyperparametersJson = hyperparametersJson;
        this.readmeContent = readmeContent;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDrawDate() {
        return drawDate;
    }

    public void setDrawDate(String drawDate) {
        this.drawDate = drawDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getHyperparametersJson() {
        return hyperparametersJson;
    }

    public void setHyperparametersJson(String hyperparametersJson) {
        this.hyperparametersJson = hyperparametersJson;
    }

    public String getReadmeContent() {
        return readmeContent;
    }

    public void setReadmeContent(String readmeContent) {
        this.readmeContent = readmeContent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
