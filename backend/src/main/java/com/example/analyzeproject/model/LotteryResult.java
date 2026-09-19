package com.example.analyzeproject.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class LotteryResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String drawId;
    private LocalDate drawDate;
    private Integer num1, num2, num3, num4, num5, num6;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    // Thêm các Getter/Setter còn lại vào đây hoặc sử dụng Lombok @Data
}
