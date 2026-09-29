package com.example.analyzeproject.repository;

import com.example.analyzeproject.model.AlgorithmHyperparameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlgorithmHyperparameterRepository extends JpaRepository<AlgorithmHyperparameter, Long> {
    List<AlgorithmHyperparameter> findAllByOrderByCreatedAtDesc();
    List<AlgorithmHyperparameter> findByCategoryOrderByCreatedAtDesc(String category);
    Optional<AlgorithmHyperparameter> findFirstByCategoryOrderByCreatedAtDesc(String category);
    Optional<AlgorithmHyperparameter> findFirstByOrderByCreatedAtDesc();
    Optional<AlgorithmHyperparameter> findByVersion(String version);
}
