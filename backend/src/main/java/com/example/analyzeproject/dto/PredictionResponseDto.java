package com.example.analyzeproject.dto;

import java.util.List;

public class PredictionResponseDto {
    private java.util.Map<String, Object> relationshipLearning;
    private java.util.Map<String, Object> commonPatternsAnalysis;
    public java.util.Map<String, Object> getRelationshipLearning() { return relationshipLearning; }
    public void setRelationshipLearning(java.util.Map<String, Object> value) { relationshipLearning = value; }
    public java.util.Map<String, Object> getCommonPatternsAnalysis() { return commonPatternsAnalysis; }
    public void setCommonPatternsAnalysis(java.util.Map<String, Object> value) { commonPatternsAnalysis = value; }
    private String status;
    private String message;
    private String drawDate;
    
    // Các thuộc tính thuật toán của bạn
    private String algorithm;
    private String algorithmName;
    private String algorithmDesc;
    private String modelVersion;
    private String hyperparameterVersion;
    
    private String category;
    private String lotteryType;
    
    // Chứa 10 số tiềm năng từ XGBoost
    private List<Integer> numbers; 
    // Chứa 10 vé đã xáo theo Wheeling System
    private List<List<Integer>> tickets; 
    
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
    private java.util.Map<String, Object> numberRelationships;
    private java.util.Map<String, Object> recommendations;
    private java.util.Map<String, Object> aiRecommendation;
    private List<Object> deviationVariables;
    private String targetDrawDate;
    private String targetDayName;
    private String targetDrawDateFormatted;
    private String baseDrawDate;
    private String baseDrawDateFormatted;
    private Boolean isTargetDrawToday;
    private java.util.Map<String, Object> pureAlgorithmTicket;
    private List<java.util.Map<String, Object>> transformedSequences;
    private java.util.Map<String, Object> primaryAlgorithmResult;
    private java.util.Map<String, Object> secondaryAlgorithmResult;
    private java.util.Map<String, Object> combinedAlgorithmResult;
    private java.util.Map<String, Object> neighborDriftAnalysis;

    // --- GETTERS & SETTERS ---

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDrawDate() { return drawDate; }
    public void setDrawDate(String drawDate) { this.drawDate = drawDate; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getAlgorithmName() { return algorithmName; }
    public void setAlgorithmName(String algorithmName) { this.algorithmName = algorithmName; }

    public String getAlgorithmDesc() { return algorithmDesc; }
    public void setAlgorithmDesc(String algorithmDesc) { this.algorithmDesc = algorithmDesc; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getHyperparameterVersion() { return hyperparameterVersion; }
    public void setHyperparameterVersion(String hyperparameterVersion) { this.hyperparameterVersion = hyperparameterVersion; }

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

    public java.util.Map<String, Object> getNumberRelationships() { return numberRelationships; }
    public void setNumberRelationships(java.util.Map<String, Object> numberRelationships) { this.numberRelationships = numberRelationships; }

    public java.util.Map<String, Object> getRecommendations() { return recommendations; }
    public void setRecommendations(java.util.Map<String, Object> recommendations) { this.recommendations = recommendations; }

    public java.util.Map<String, Object> getAiRecommendation() { return aiRecommendation; }
    public void setAiRecommendation(java.util.Map<String, Object> aiRecommendation) { this.aiRecommendation = aiRecommendation; }

    public List<Object> getDeviationVariables() { return deviationVariables; }
    public void setDeviationVariables(List<Object> deviationVariables) { this.deviationVariables = deviationVariables; }

    public String getTargetDrawDate() { return targetDrawDate; }
    public void setTargetDrawDate(String targetDrawDate) { this.targetDrawDate = targetDrawDate; }

    public String getTargetDayName() { return targetDayName; }
    public void setTargetDayName(String targetDayName) { this.targetDayName = targetDayName; }

    public String getTargetDrawDateFormatted() { return targetDrawDateFormatted; }
    public void setTargetDrawDateFormatted(String targetDrawDateFormatted) { this.targetDrawDateFormatted = targetDrawDateFormatted; }

    public String getBaseDrawDate() { return baseDrawDate; }
    public void setBaseDrawDate(String baseDrawDate) { this.baseDrawDate = baseDrawDate; }

    public String getBaseDrawDateFormatted() { return baseDrawDateFormatted; }
    public void setBaseDrawDateFormatted(String baseDrawDateFormatted) { this.baseDrawDateFormatted = baseDrawDateFormatted; }

    public Boolean getIsTargetDrawToday() { return isTargetDrawToday; }
    public void setIsTargetDrawToday(Boolean isTargetDrawToday) { this.isTargetDrawToday = isTargetDrawToday; }

    public java.util.Map<String, Object> getPureAlgorithmTicket() { return pureAlgorithmTicket; }
    public void setPureAlgorithmTicket(java.util.Map<String, Object> pureAlgorithmTicket) { this.pureAlgorithmTicket = pureAlgorithmTicket; }

    public List<java.util.Map<String, Object>> getTransformedSequences() { return transformedSequences; }
    public void setTransformedSequences(List<java.util.Map<String, Object>> transformedSequences) { this.transformedSequences = transformedSequences; }

    public java.util.Map<String, Object> getPrimaryAlgorithmResult() { return primaryAlgorithmResult; }
    public void setPrimaryAlgorithmResult(java.util.Map<String, Object> primaryAlgorithmResult) { this.primaryAlgorithmResult = primaryAlgorithmResult; }

    public java.util.Map<String, Object> getSecondaryAlgorithmResult() { return secondaryAlgorithmResult; }
    public void setSecondaryAlgorithmResult(java.util.Map<String, Object> secondaryAlgorithmResult) { this.secondaryAlgorithmResult = secondaryAlgorithmResult; }

    public java.util.Map<String, Object> getCombinedAlgorithmResult() { return combinedAlgorithmResult; }
    public void setCombinedAlgorithmResult(java.util.Map<String, Object> combinedAlgorithmResult) { this.combinedAlgorithmResult = combinedAlgorithmResult; }

    public java.util.Map<String, Object> getNeighborDriftAnalysis() { return neighborDriftAnalysis; }
    public void setNeighborDriftAnalysis(java.util.Map<String, Object> neighborDriftAnalysis) { this.neighborDriftAnalysis = neighborDriftAnalysis; }
}
