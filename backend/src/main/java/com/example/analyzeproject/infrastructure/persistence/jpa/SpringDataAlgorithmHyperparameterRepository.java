package com.example.analyzeproject.infrastructure.persistence.jpa;

import com.example.analyzeproject.infrastructure.persistence.entity.AlgorithmHyperparameterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataAlgorithmHyperparameterRepository extends JpaRepository<AlgorithmHyperparameterEntity, Long> {
    List<AlgorithmHyperparameterEntity> findAllByOrderByCreatedAtDesc();
    List<AlgorithmHyperparameterEntity> findByCategoryOrderByCreatedAtDesc(String category);
    Optional<AlgorithmHyperparameterEntity> findFirstByCategoryOrderByCreatedAtDesc(String category);
    Optional<AlgorithmHyperparameterEntity> findFirstByOrderByCreatedAtDesc();
    Optional<AlgorithmHyperparameterEntity> findByVersion(String version);
}
