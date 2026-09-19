package com.example.analyzeproject.repository;

import com.example.analyzeproject.model.LotteryNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LotteryNumberRepository extends JpaRepository<LotteryNumber, Long> {
    List<LotteryNumber> findAllByOrderByDrawDateDescCreatedAtDesc();
    List<LotteryNumber> findByDrawDateOrderByCreatedAtDesc(LocalDate drawDate);
    List<LotteryNumber> findByCategoryOrderByDrawDateDescCreatedAtDesc(String category);
    List<LotteryNumber> findByDrawDateAndCategoryOrderByCreatedAtDesc(LocalDate drawDate, String category);
}
