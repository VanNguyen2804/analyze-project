package com.example.analyzeproject.dto;

import java.util.List;

public class TicketCheckResponseDto {
    private String status;
    private String message;
    private List<Integer> officialNumbers;
    private Integer officialSpecialNumber;
    private List<TicketResult> results;

    public static class TicketResult {
        private List<Integer> userNumbers;
        private int matchCount;
        private boolean matchSpecial;
        private String prize;

        public TicketResult(List<Integer> userNumbers, int matchCount, boolean matchSpecial, String prize) {
            this.userNumbers = userNumbers;
            this.matchCount = matchCount;
            this.matchSpecial = matchSpecial;
            this.prize = prize;
        }
        // Getters
        public List<Integer> getUserNumbers() { return userNumbers; }
        public int getMatchCount() { return matchCount; }
        public boolean isMatchSpecial() { return matchSpecial; }
        public String getPrize() { return prize; }
    }

    // Getters and Setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<Integer> getOfficialNumbers() { return officialNumbers; }
    public void setOfficialNumbers(List<Integer> officialNumbers) { this.officialNumbers = officialNumbers; }
    public Integer getOfficialSpecialNumber() { return officialSpecialNumber; }
    public void setOfficialSpecialNumber(Integer officialSpecialNumber) { this.officialSpecialNumber = officialSpecialNumber; }
    public List<TicketResult> getResults() { return results; }
    public void setResults(List<TicketResult> results) { this.results = results; }
}