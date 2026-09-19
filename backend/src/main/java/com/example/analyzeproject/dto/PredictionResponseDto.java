package com.example.analyzeproject.dto;

import java.util.ArrayList;
import java.util.List;

public class PredictionResponseDto {
    private String category; // MEGA or POWER
    private List<Integer> numbers = new ArrayList<>();
    private Integer specialNumber; // Added for POWER category (Jackpot 2)
    private int totalDrawsAnalyzed;
    private List<Integer> hotNumbers = new ArrayList<>();
    private List<Integer> coldNumbers = new ArrayList<>();
    private List<Integer> specialHotNumbers = new ArrayList<>();
    private List<String> frequentPairs = new ArrayList<>();
    private List<String> jackpot2Pairs = new ArrayList<>();
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

    public Integer getSpecialNumber() {
        return specialNumber;
    }

    public void setSpecialNumber(Integer specialNumber) {
        this.specialNumber = specialNumber;
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

    public List<Integer> getSpecialHotNumbers() {
        return specialHotNumbers;
    }

    public void setSpecialHotNumbers(List<Integer> specialHotNumbers) {
        this.specialHotNumbers = specialHotNumbers;
    }

    public List<String> getFrequentPairs() {
        return frequentPairs;
    }

    public void setFrequentPairs(List<String> frequentPairs) {
        this.frequentPairs = frequentPairs;
    }

    public List<String> getJackpot2Pairs() {
        return jackpot2Pairs;
    }

    public void setJackpot2Pairs(List<String> jackpot2Pairs) {
        this.jackpot2Pairs = jackpot2Pairs;
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
