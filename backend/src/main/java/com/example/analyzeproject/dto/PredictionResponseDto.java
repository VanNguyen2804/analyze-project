package com.example.analyzeproject.dto;

import java.util.ArrayList;
import java.util.List;

public class PredictionResponseDto {
    private String category; // MEGA or POWER
    private List<Integer> numbers = new ArrayList<>();
    private int totalDrawsAnalyzed;
    private List<Integer> hotNumbers = new ArrayList<>();
    private List<Integer> coldNumbers = new ArrayList<>();
    private List<String> frequentPairs = new ArrayList<>();
    private String oddEvenRatio;
    private String analysisSummary;
    private List<NumberScoreDetailDto> details = new ArrayList<>();

    public PredictionResponseDto() {}

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

    public int getTotalDrawsAnalyzed() {
        return totalDrawsAnalyzed;
    }

    public void setTotalDrawsAnalyzed(int totalDrawsAnalyzed) {
        this.totalDrawsAnalyzed = totalDrawsAnalyzed;
    }

    public List<Integer> getHotNumbers() {
        return hotNumbers;
    }

    public void setHotNumbers(List<Integer> hotNumbers) {
        this.hotNumbers = hotNumbers;
    }

    public List<Integer> getColdNumbers() {
        return coldNumbers;
    }

    public void setColdNumbers(List<Integer> coldNumbers) {
        this.coldNumbers = coldNumbers;
    }

    public List<String> getFrequentPairs() {
        return frequentPairs;
    }

    public void setFrequentPairs(List<String> frequentPairs) {
        this.frequentPairs = frequentPairs;
    }

    public String getOddEvenRatio() {
        return oddEvenRatio;
    }

    public void setOddEvenRatio(String oddEvenRatio) {
        this.oddEvenRatio = oddEvenRatio;
    }

    public String getAnalysisSummary() {
        return analysisSummary;
    }

    public void setAnalysisSummary(String analysisSummary) {
        this.analysisSummary = analysisSummary;
    }

    public List<NumberScoreDetailDto> getDetails() {
        return details;
    }

    public void setDetails(List<NumberScoreDetailDto> details) {
        this.details = details;
    }
}
