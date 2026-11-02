package com.example.analyzeproject.application.port.out;

import com.example.analyzeproject.domain.model.UserTicket;
import java.time.LocalDate;
import java.util.*;

public interface UserTicketRepository {
    UserTicket save(UserTicket value);
    Optional<UserTicket> findById(Long id);
    long count();
    void deleteAll();
    List<UserTicket> findAllByOrderByCheckedAtDesc();
}
