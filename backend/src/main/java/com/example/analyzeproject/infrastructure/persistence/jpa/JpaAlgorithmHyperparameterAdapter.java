package com.example.analyzeproject.infrastructure.persistence.jpa;

import com.example.analyzeproject.application.port.out.AlgorithmHyperparameterRepository;
import com.example.analyzeproject.domain.model.AlgorithmHyperparameter;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;

@Repository
public class JpaAlgorithmHyperparameterAdapter implements AlgorithmHyperparameterRepository {
    private final SpringDataAlgorithmHyperparameterRepository repository;
    private final PersistenceMapper mapper = new PersistenceMapper();
    public JpaAlgorithmHyperparameterAdapter(SpringDataAlgorithmHyperparameterRepository repository) { this.repository = repository; }
    @Override public AlgorithmHyperparameter save(AlgorithmHyperparameter value) { return mapper.toDomain(repository.save(mapper.toEntity(value))); }
    @Override public Optional<AlgorithmHyperparameter> findById(Long id) { return repository.findById(id).map(mapper::toDomain); }
    @Override public long count() { return repository.count(); }
    @Override public List<AlgorithmHyperparameter> findAllByOrderByCreatedAtDesc() { return repository.findAllByOrderByCreatedAtDesc() .stream().map(mapper::toDomain).toList(); }
    @Override public List<AlgorithmHyperparameter> findByCategoryOrderByCreatedAtDesc(String category) { return repository.findByCategoryOrderByCreatedAtDesc(category) .stream().map(mapper::toDomain).toList(); }
    @Override public Optional<AlgorithmHyperparameter> findFirstByCategoryOrderByCreatedAtDesc(String category) { return repository.findFirstByCategoryOrderByCreatedAtDesc(category).map(mapper::toDomain); }
    @Override public Optional<AlgorithmHyperparameter> findFirstByOrderByCreatedAtDesc() { return repository.findFirstByOrderByCreatedAtDesc().map(mapper::toDomain); }
    @Override public Optional<AlgorithmHyperparameter> findByVersion(String version) { return repository.findByVersion(version).map(mapper::toDomain); }
}
