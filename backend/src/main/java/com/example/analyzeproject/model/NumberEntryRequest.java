package com.example.analyzeproject.model;

import java.util.List;

public class NumberEntryRequest {
    private List<Integer> numbers;
    private String note;

    public NumberEntryRequest() {}

    public NumberEntryRequest(List<Integer> numbers, String note) {
        this.numbers = numbers;
        this.note = note;
    }

    public List<Integer> getNumbers() {
        return numbers;
    }

    public void setNumbers(List<Integer> numbers) {
        this.numbers = numbers;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
