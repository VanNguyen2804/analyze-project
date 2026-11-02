package com.example.analyzeproject.infrastructure.persistence.jpa;

import com.example.analyzeproject.infrastructure.persistence.entity.LotteryNumberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SpringDataLotteryNumberRepository extends JpaRepository<LotteryNumberEntity, Long> {
    List<LotteryNumberEntity> findAllByOrderByDrawDateDescCreatedAtDesc();
    List<LotteryNumberEntity> findByDrawDateOrderByCreatedAtDesc(LocalDate drawDate);
    List<LotteryNumberEntity> findByCategoryOrderByDrawDateDescCreatedAtDesc(String category);
    List<LotteryNumberEntity> findByDrawDateAndCategoryOrderByCreatedAtDesc(LocalDate drawDate, String category);
}
