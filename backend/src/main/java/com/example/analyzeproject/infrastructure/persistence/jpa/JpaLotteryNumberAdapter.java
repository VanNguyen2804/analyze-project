package com.example.analyzeproject.infrastructure.persistence.jpa;

import com.example.analyzeproject.application.port.out.LotteryNumberRepository;
import com.example.analyzeproject.domain.model.LotteryNumber;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;

@Repository
public class JpaLotteryNumberAdapter implements LotteryNumberRepository {
    private final SpringDataLotteryNumberRepository repository;
    private final PersistenceMapper mapper = new PersistenceMapper();
    public JpaLotteryNumberAdapter(SpringDataLotteryNumberRepository repository) { this.repository = repository; }
    @Override public LotteryNumber save(LotteryNumber value) { return mapper.toDomain(repository.save(mapper.toEntity(value))); }
    @Override public Optional<LotteryNumber> findById(Long id) { return repository.findById(id).map(mapper::toDomain); }
    @Override public long count() { return repository.count(); }
    @Override public List<LotteryNumber> saveAll(Iterable<LotteryNumber> values) { return repository.saveAll(java.util.stream.StreamSupport.stream(values.spliterator(), false).map(mapper::toEntity).toList()).stream().map(mapper::toDomain).toList(); }
    @Override public boolean existsById(Long id) { return repository.existsById(id); }
    @Override public void deleteById(Long id) { repository.deleteById(id); }
    @Override public List<LotteryNumber> findAllByOrderByDrawDateDescCreatedAtDesc() { return repository.findAllByOrderByDrawDateDescCreatedAtDesc() .stream().map(mapper::toDomain).toList(); }
    @Override public List<LotteryNumber> findByDrawDateOrderByCreatedAtDesc(LocalDate drawDate) { return repository.findByDrawDateOrderByCreatedAtDesc(drawDate) .stream().map(mapper::toDomain).toList(); }
    @Override public List<LotteryNumber> findByCategoryOrderByDrawDateDescCreatedAtDesc(String category) { return repository.findByCategoryOrderByDrawDateDescCreatedAtDesc(category) .stream().map(mapper::toDomain).toList(); }
    @Override public List<LotteryNumber> findByDrawDateAndCategoryOrderByCreatedAtDesc(LocalDate drawDate, String category) { return repository.findByDrawDateAndCategoryOrderByCreatedAtDesc(drawDate,category) .stream().map(mapper::toDomain).toList(); }
}
