package com.example.analyzeproject.application.port.out;

import com.example.analyzeproject.domain.model.AlgorithmHyperparameter;
import java.time.LocalDate;
import java.util.*;

public interface AlgorithmHyperparameterRepository {
    AlgorithmHyperparameter save(AlgorithmHyperparameter value);
    Optional<AlgorithmHyperparameter> findById(Long id);
    long count();
    List<AlgorithmHyperparameter> findAllByOrderByCreatedAtDesc();
    List<AlgorithmHyperparameter> findByCategoryOrderByCreatedAtDesc(String category);
    Optional<AlgorithmHyperparameter> findFirstByCategoryOrderByCreatedAtDesc(String category);
    Optional<AlgorithmHyperparameter> findFirstByOrderByCreatedAtDesc();
    Optional<AlgorithmHyperparameter> findByVersion(String version);
}
