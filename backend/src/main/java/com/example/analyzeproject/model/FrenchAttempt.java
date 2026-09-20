package com.example.analyzeproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class FrenchAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long exerciseId;
    private String expectedSentence;
    private String userInput;
    private boolean isCorrect;
    private LocalDateTime attemptedAt;

    // Getters & Setters (Bổ sung đầy đủ trong file của bạn)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExerciseId() { return exerciseId; }
    public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }
    public String getExpectedSentence() { return expectedSentence; }
    public void setExpectedSentence(String expectedSentence) { this.expectedSentence = expectedSentence; }
    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }
    public boolean isCorrect() { return isCorrect; }
    public void setCorrect(boolean correct) { isCorrect = correct; }
    public LocalDateTime getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(LocalDateTime attemptedAt) { this.attemptedAt = attemptedAt; }
}