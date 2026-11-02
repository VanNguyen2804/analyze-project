package com.example.analyzeproject.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class LotteryNumber {

    private Long id;

    private LocalDate drawDate;

    private String category; // "MEGA" (1-45) or "POWER" (1-55)

    private List<Integer> numbers = new ArrayList<>();

    private Integer specialNumber; // For POWER category (1-55, distinct from numbers)

    private LocalDateTime createdAt;

    private String note;

    private java.util.Map<String,Object> quality;

    public java.util.Map<String,Object> getQuality() { return quality; }
    public void setQuality(java.util.Map<String,Object> quality) { this.quality = quality; }

    public LotteryNumber() {
        this.createdAt = LocalDateTime.now();
        this.drawDate = LocalDate.now();
        this.category = "MEGA";
    }

    public LotteryNumber(LocalDate drawDate, String category, List<Integer> numbers, Integer specialNumber, String note) {
        this.drawDate = drawDate != null ? drawDate : LocalDate.now();
        this.category = category != null ? category.toUpperCase() : "MEGA";
        this.numbers = numbers;
        this.specialNumber = specialNumber;
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

    public LocalDate getDrawDate() {
        return drawDate;
    }

    public void setDrawDate(LocalDate drawDate) {
        this.drawDate = drawDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<Integer> getNumbers() {
        return numbers;
    }

    public void setNumbers(List<Integer> numbers) {
        this.numbers = numbers;
    }

    public Integer getSpecialNumber() {
        return specialNumber;
    }

    public void setSpecialNumber(Integer specialNumber) {
        this.specialNumber = specialNumber;
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
