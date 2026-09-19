package com.example.analyzeproject.repository;

import com.example.analyzeproject.model.LotteryNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LotteryNumberRepository extends JpaRepository<LotteryNumber, Long> {
    List<LotteryNumber> findAllByOrderByCreatedAtDesc();
}
