package com.example.analyzeproject.dto;

public class NumberSelectionReasonDto {
    private int number;
    private String role; // "main" or "special"
    private String tag;
    private String title;
    private String reason;
    private double probabilityPercent;
    private int frequency;
    private int drawGap;

    public NumberSelectionReasonDto() {}

    public NumberSelectionReasonDto(int number, String role, String tag, String title, String reason, double probabilityPercent, int frequency, int drawGap) {
        this.number = number;
        this.role = role;
        this.tag = tag;
        this.title = title;
        this.reason = reason;
        this.probabilityPercent = probabilityPercent;
        this.frequency = frequency;
        this.drawGap = drawGap;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public double getProbabilityPercent() {
        return probabilityPercent;
    }

    public void setProbabilityPercent(double probabilityPercent) {
        this.probabilityPercent = probabilityPercent;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public int getDrawGap() {
        return drawGap;
    }

    public void setDrawGap(int drawGap) {
        this.drawGap = drawGap;
    }
}
