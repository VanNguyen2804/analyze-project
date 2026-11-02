package com.example.analyzeproject.application.port.out;

import com.example.analyzeproject.domain.model.LotteryNumber;
import java.time.LocalDate;
import java.util.*;

public interface LotteryNumberRepository {
    LotteryNumber save(LotteryNumber value);
    Optional<LotteryNumber> findById(Long id);
    long count();
    List<LotteryNumber> saveAll(Iterable<LotteryNumber> values);
    boolean existsById(Long id);
    void deleteById(Long id);
    List<LotteryNumber> findAllByOrderByDrawDateDescCreatedAtDesc();
    List<LotteryNumber> findByDrawDateOrderByCreatedAtDesc(LocalDate drawDate);
    List<LotteryNumber> findByCategoryOrderByDrawDateDescCreatedAtDesc(String category);
    List<LotteryNumber> findByDrawDateAndCategoryOrderByCreatedAtDesc(LocalDate drawDate, String category);
}
