package com.example.analyzeproject.repository;
import com.example.analyzeproject.model.FrenchExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FrenchExerciseRepository extends JpaRepository<FrenchExercise, Long> {
    List<FrenchExercise> findByCategoryIn(List<String> categories);
}