package com.example.analyzeproject.dto;

import java.util.List;

public class PredictionResponseDto {
    private String status;
    private String category;
    private String lotteryType;
    private List<Integer> numbers; // Chứa 10 số tiềm năng từ XGBoost
    private List<List<Integer>> tickets; // Chứa 10 vé đã xáo theo Wheeling System
    private Integer specialNumber;
    private int totalDrawsAnalyzed;
    
    private List<Integer> hotNumbers;
    private List<Integer> coldNumbers;
    private List<Integer> specialHotNumbers;
    private List<String> frequentPairs;
    private List<String> jackpot2Pairs;
    
    private String oddEvenRatio;
    private List<NumberScoreDetailDto> details;
    private List<NumberSelectionReasonDto> selectionReasons;
    
    private String analysisSummary;
    private String overallReason;
    private List<DrawRecordDto> recentDraws;

    // --- GETTERS & SETTERS ---

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getLotteryType() { return lotteryType; }
    public void setLotteryType(String lotteryType) { this.lotteryType = lotteryType; }

    public List<Integer> getNumbers() { return numbers; }
    public void setNumbers(List<Integer> numbers) { this.numbers = numbers; }

    public List<List<Integer>> getTickets() { return tickets; }
    public void setTickets(List<List<Integer>> tickets) { this.tickets = tickets; }

    public Integer getSpecialNumber() { return specialNumber; }
    public void setSpecialNumber(Integer specialNumber) { this.specialNumber = specialNumber; }

    public int getTotalDrawsAnalyzed() { return totalDrawsAnalyzed; }
    public void setTotalDrawsAnalyzed(int totalDrawsAnalyzed) { this.totalDrawsAnalyzed = totalDrawsAnalyzed; }

    public List<Integer> getHotNumbers() { return hotNumbers; }
    public void setHotNumbers(List<Integer> hotNumbers) { this.hotNumbers = hotNumbers; }

    public List<Integer> getColdNumbers() { return coldNumbers; }
    public void setColdNumbers(List<Integer> coldNumbers) { this.coldNumbers = coldNumbers; }

    public List<Integer> getSpecialHotNumbers() { return specialHotNumbers; }
    public void setSpecialHotNumbers(List<Integer> specialHotNumbers) { this.specialHotNumbers = specialHotNumbers; }

    public List<String> getFrequentPairs() { return frequentPairs; }
    public void setFrequentPairs(List<String> frequentPairs) { this.frequentPairs = frequentPairs; }

    public List<String> getJackpot2Pairs() { return jackpot2Pairs; }
    public void setJackpot2Pairs(List<String> jackpot2Pairs) { this.jackpot2Pairs = jackpot2Pairs; }

    public String getOddEvenRatio() { return oddEvenRatio; }
    public void setOddEvenRatio(String oddEvenRatio) { this.oddEvenRatio = oddEvenRatio; }

    public List<NumberScoreDetailDto> getDetails() { return details; }
    public void setDetails(List<NumberScoreDetailDto> details) { this.details = details; }

    public List<NumberSelectionReasonDto> getSelectionReasons() { return selectionReasons; }
    public void setSelectionReasons(List<NumberSelectionReasonDto> selectionReasons) { this.selectionReasons = selectionReasons; }

    public String getAnalysisSummary() { return analysisSummary; }
    public void setAnalysisSummary(String analysisSummary) { this.analysisSummary = analysisSummary; }

    public String getOverallReason() { return overallReason; }
    public void setOverallReason(String overallReason) { this.overallReason = overallReason; }

    public List<DrawRecordDto> getRecentDraws() { return recentDraws; }
    public void setRecentDraws(List<DrawRecordDto> recentDraws) { this.recentDraws = recentDraws; }
}
