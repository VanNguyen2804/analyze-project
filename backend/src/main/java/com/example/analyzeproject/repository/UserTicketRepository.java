package com.example.analyzeproject.repository;

import com.example.analyzeproject.model.UserTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserTicketRepository extends JpaRepository<UserTicket, Long> {
    List<UserTicket> findAllByOrderByCheckedAtDesc();
}