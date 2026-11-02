package com.example.analyzeproject.domain.model;

import java.time.LocalDateTime;

public class AlgorithmHyperparameter {

    private Long id;

    private String version; // e.g. "v1.1.0"

    private String drawDate; // e.g. "2026-09-28"

    private String category; // "POWER", "MEGA", or "ALL"

    private String model; // "XGBoost Multi-Factor Optimization"

    private String hyperparametersJson;

    private String readmeContent;

    private LocalDateTime createdAt;

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
