package com.example.analyzeproject.model;

import java.time.LocalDate;
import java.util.List;

public class NumberEntryRequest {
    private List<Integer> numbers;
    private Integer specialNumber; // For POWER category
    private String note;
    private LocalDate drawDate;
    private String category; // "MEGA" or "POWER"

    public NumberEntryRequest() {}

    public NumberEntryRequest(List<Integer> numbers, Integer specialNumber, String note, LocalDate drawDate, String category) {
        this.numbers = numbers;
        this.specialNumber = specialNumber;
        this.note = note;
        this.drawDate = drawDate;
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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
}
