package com.example.analyzeproject.dto;

import java.util.List;

public class TicketCheckRequestDto {
    private String category; // MEGA hoặc POWER
    private String drawDate; // yyyy-MM-dd
    private List<List<Integer>> tickets;

    // Getters and Setters
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDrawDate() { return drawDate; }
    public void setDrawDate(String drawDate) { this.drawDate = drawDate; }
    public List<List<Integer>> getTickets() { return tickets; }
    public void setTickets(List<List<Integer>> tickets) { this.tickets = tickets; }
}