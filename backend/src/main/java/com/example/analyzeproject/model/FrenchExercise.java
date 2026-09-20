package com.example.analyzeproject.model;

import jakarta.persistence.*;

@Entity
public class FrenchExercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String category; // GREETING, HEALTH, FOOD
    private String level;
    private String sentence; // Câu tiếng Pháp (Câu trả lời đúng)
    private String translation;

    public FrenchExercise() {}
    public FrenchExercise(String category, String level, String sentence, String translation) {
        this.category = category; this.level = level; 
        this.sentence = sentence; this.translation = translation;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public String getCategory() { return category; }
    public String getLevel() { return level; }
    public String getSentence() { return sentence; }
    public String getTranslation() { return translation; }
}