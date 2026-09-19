package com.example.analyzeproject.dto;

import java.util.List;

public class DrawRecordDto {
    private Long id;
    private String drawDate;
    private List<Integer> numbers;
    private Integer specialNumber;
    private String note;

    public DrawRecordDto() {}

    public DrawRecordDto(Long id, String drawDate, List<Integer> numbers, Integer specialNumber, String note) {
        this.id = id;
        this.drawDate = drawDate;
        this.numbers = numbers;
        this.specialNumber = specialNumber;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDrawDate() {
        return drawDate;
    }

    public void setDrawDate(String drawDate) {
        this.drawDate = drawDate;
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
}
