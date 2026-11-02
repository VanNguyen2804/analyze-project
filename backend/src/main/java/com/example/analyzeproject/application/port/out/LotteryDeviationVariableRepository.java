package com.example.analyzeproject.application.port.out;

import com.example.analyzeproject.domain.model.LotteryDeviationVariable;
import java.time.LocalDate;
import java.util.*;

public interface LotteryDeviationVariableRepository {
    LotteryDeviationVariable save(LotteryDeviationVariable value);
    Optional<LotteryDeviationVariable> findById(Long id);
    long count();
    List<LotteryDeviationVariable> findAllByOrderByCreatedAtDesc();
    List<LotteryDeviationVariable> findByCategoryOrderByCreatedAtDesc(String category);
    List<LotteryDeviationVariable> findByCategoryAndTargetDrawDateOrderByCreatedAtDesc(String category, String targetDrawDate);
}
