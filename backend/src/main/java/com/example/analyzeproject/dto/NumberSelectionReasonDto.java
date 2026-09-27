package com.example.analyzeproject.dto;

public class NumberSelectionReasonDto {
    private int number;
    private String role; // "main" hoặc "special"
    private String tag;
    private String title;
    private String reason;
    private double probabilityPercent;
    private int frequency;
    private int drawGap;
    
    // CÁC THUỘC TÍNH MỚI CHO GIAO DIỆN CHI TIẾT (image_3fa87e.png)
    private int rank;
    private double momentum;
    private int markov;
    private int poisson;
    private int companion;
    private String pairedNumbers;

    public NumberSelectionReasonDto() {}

    public NumberSelectionReasonDto(int number, String role, String tag, String title, String reason, 
                                    double probabilityPercent, int frequency, int drawGap) {
        this.number = number;
        this.role = role;
        this.tag = tag;
        this.title = title;
        this.reason = reason;
        this.probabilityPercent = probabilityPercent;
        this.frequency = frequency;
        this.drawGap = drawGap;
    }

    // --- GETTERS & SETTERS ---
    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public double getProbabilityPercent() { return probabilityPercent; }
    public void setProbabilityPercent(double probabilityPercent) { this.probabilityPercent = probabilityPercent; }

    public int getFrequency() { return frequency; }
    public void setFrequency(int frequency) { this.frequency = frequency; }

    public int getDrawGap() { return drawGap; }
    public void setDrawGap(int drawGap) { this.drawGap = drawGap; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public double getMomentum() { return momentum; }
    public void setMomentum(double momentum) { this.momentum = momentum; }

    public int getMarkov() { return markov; }
    public void setMarkov(int markov) { this.markov = markov; }

    public int getPoisson() { return poisson; }
    public void setPoisson(int poisson) { this.poisson = poisson; }

    public int getCompanion() { return companion; }
    public void setCompanion(int companion) { this.companion = companion; }

    public String getPairedNumbers() { return pairedNumbers; }
    public void setPairedNumbers(String pairedNumbers) { this.pairedNumbers = pairedNumbers; }
}