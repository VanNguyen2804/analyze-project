package com.example.analyzeproject.infrastructure.persistence.jpa;

import com.example.analyzeproject.infrastructure.persistence.entity.UserTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SpringDataUserTicketRepository extends JpaRepository<UserTicketEntity, Long> {
    List<UserTicketEntity> findAllByOrderByCheckedAtDesc();
}