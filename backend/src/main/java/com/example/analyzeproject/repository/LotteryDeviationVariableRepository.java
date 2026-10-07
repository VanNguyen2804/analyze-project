package com.example.analyzeproject.repository;

import com.example.analyzeproject.model.LotteryDeviationVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LotteryDeviationVariableRepository extends JpaRepository<LotteryDeviationVariable, Long> {
    List<LotteryDeviationVariable> findAllByOrderByCreatedAtDesc();
    List<LotteryDeviationVariable> findByCategoryOrderByCreatedAtDesc(String category);
    List<LotteryDeviationVariable> findByCategoryAndTargetDrawDateOrderByCreatedAtDesc(String category, String targetDrawDate);
}
