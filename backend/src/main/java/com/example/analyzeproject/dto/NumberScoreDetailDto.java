package com.example.analyzeproject.dto;

public class NumberScoreDetailDto {
    private int number;
    private double probabilityPercent;
    private int frequency;
    private int drawGap;
    private String tag; // "SỐ NÓNG", "LÔ GAN", "CẶP ĐI KÈM", "CÂN BẰNG"

    public NumberScoreDetailDto() {}

    public NumberScoreDetailDto(int number, double probabilityPercent, int frequency, int drawGap, String tag) {
        this.number = number;
        this.probabilityPercent = probabilityPercent;
        this.frequency = frequency;
        this.drawGap = drawGap;
        this.tag = tag;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
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

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}
