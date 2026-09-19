package com.example.analyzeproject.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lottery_numbers")
public class LotteryNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draw_date", nullable = false)
    private LocalDate drawDate;

    @Column(name = "category", nullable = false)
    private String category; // "MEGA" (1-45) or "POWER" (1-55)

    @ElementCollection
    @CollectionTable(name = "lottery_selected_numbers", joinColumns = @JoinColumn(name = "lottery_id"))
    @Column(name = "number_value")
    @OrderColumn(name = "number_order")
    private List<Integer> numbers = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "note")
    private String note;

    public LotteryNumber() {
        this.createdAt = LocalDateTime.now();
        this.drawDate = LocalDate.now();
        this.category = "MEGA";
    }

    public LotteryNumber(LocalDate drawDate, String category, List<Integer> numbers, String note) {
        this.drawDate = drawDate != null ? drawDate : LocalDate.now();
        this.category = category != null ? category.toUpperCase() : "MEGA";
        this.numbers = numbers;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDrawDate() {
        return drawDate;
    }

    public void setDrawDate(LocalDate drawDate) {
        this.drawDate = drawDate;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
