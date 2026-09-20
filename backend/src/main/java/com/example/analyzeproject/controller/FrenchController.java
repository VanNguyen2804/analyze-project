package com.example.analyzeproject.controller;

import com.example.analyzeproject.model.FrenchAttempt;
import com.example.analyzeproject.model.FrenchExercise;
import com.example.analyzeproject.repository.FrenchAttemptRepository;
import com.example.analyzeproject.repository.FrenchExerciseRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/french")
@CrossOrigin(origins = "*")
public class FrenchController {
    
    private final FrenchExerciseRepository exerciseRepo;
    private final FrenchAttemptRepository attemptRepo;

    public FrenchController(FrenchExerciseRepository exerciseRepo, FrenchAttemptRepository attemptRepo) {
        this.exerciseRepo = exerciseRepo;
        this.attemptRepo = attemptRepo;
    }

    // Tự động nạp dữ liệu mẫu vào H2 DB
    @PostConstruct
    public void initDatabase() {
        if (exerciseRepo.count() == 0) {
            exerciseRepo.save(new FrenchExercise("GREETING", "Cơ bản", "Bonjour", "Xin chào"));
            exerciseRepo.save(new FrenchExercise("GREETING", "Cơ bản", "Bonsoir", "Chào buổi tối"));
            exerciseRepo.save(new FrenchExercise("HEALTH", "Cơ bản", "Comment allez-vous?", "Bạn có khỏe không?"));
            exerciseRepo.save(new FrenchExercise("HEALTH", "Cơ bản", "Je vais bien, merci.", "Tôi khỏe, cảm ơn."));
            exerciseRepo.save(new FrenchExercise("FOOD", "Trung bình", "Je voudrais un café, s'il vous plaît.", "Cho tôi một ly cà phê, làm ơn."));
            exerciseRepo.save(new FrenchExercise("FOOD", "Trung bình", "L'addition, s'il vous plaît.", "Làm ơn tính tiền."));
        }
    }

    @GetMapping("/exercises")
    public List<FrenchExercise> getExercises(@RequestParam(required = false) String category) {
        if (category == null || category.equalsIgnoreCase("ALL")) {
            return exerciseRepo.findAll();
        }
        return exerciseRepo.findByCategoryIn(Arrays.asList(category.split(",")));
    }

    // Xử lý chấm điểm và lưu lịch sử
    @PostMapping("/attempt")
    public FrenchAttempt submitAttempt(@RequestBody FrenchAttempt attempt) {
        FrenchExercise exercise = exerciseRepo.findById(attempt.getExerciseId()).orElseThrow();
        
        // Logic chấm điểm bỏ qua dấu câu và in hoa
        String normalizedExpected = exercise.getSentence().toLowerCase().replaceAll("[.,!?']", " ").replaceAll("\\s+", " ").trim();
        String normalizedInput = attempt.getUserInput().toLowerCase().replaceAll("[.,!?']", " ").replaceAll("\\s+", " ").trim();
        
        attempt.setExpectedSentence(exercise.getSentence());
        attempt.setCorrect(normalizedExpected.equals(normalizedInput));
        attempt.setAttemptedAt(LocalDateTime.now());
        
        return attemptRepo.save(attempt);
    }
    
    @GetMapping("/history")
    public List<FrenchAttempt> getHistory() {
        return attemptRepo.findAllByOrderByAttemptedAtDesc();
    }
}