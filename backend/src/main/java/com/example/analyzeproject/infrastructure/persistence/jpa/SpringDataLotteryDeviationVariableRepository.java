package com.example.analyzeproject.infrastructure.persistence.jpa;

import com.example.analyzeproject.infrastructure.persistence.entity.LotteryDeviationVariableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataLotteryDeviationVariableRepository extends JpaRepository<LotteryDeviationVariableEntity, Long> {
    List<LotteryDeviationVariableEntity> findAllByOrderByCreatedAtDesc();
    List<LotteryDeviationVariableEntity> findByCategoryOrderByCreatedAtDesc(String category);
    List<LotteryDeviationVariableEntity> findByCategoryAndTargetDrawDateOrderByCreatedAtDesc(String category, String targetDrawDate);
}
