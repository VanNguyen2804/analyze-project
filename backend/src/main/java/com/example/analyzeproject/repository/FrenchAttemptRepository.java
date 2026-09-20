package com.example.analyzeproject.repository;
import com.example.analyzeproject.model.FrenchAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FrenchAttemptRepository extends JpaRepository<FrenchAttempt, Long> {
    List<FrenchAttempt> findAllByOrderByAttemptedAtDesc();
}