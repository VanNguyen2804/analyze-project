package com.example.analyzeproject.infrastructure.persistence.jpa;

import com.example.analyzeproject.application.port.out.UserTicketRepository;
import com.example.analyzeproject.domain.model.UserTicket;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;

@Repository
public class JpaUserTicketAdapter implements UserTicketRepository {
    private final SpringDataUserTicketRepository repository;
    private final PersistenceMapper mapper = new PersistenceMapper();
    public JpaUserTicketAdapter(SpringDataUserTicketRepository repository) { this.repository = repository; }
    @Override public UserTicket save(UserTicket value) { return mapper.toDomain(repository.save(mapper.toEntity(value))); }
    @Override public Optional<UserTicket> findById(Long id) { return repository.findById(id).map(mapper::toDomain); }
    @Override public long count() { return repository.count(); }
    @Override public void deleteAll() { repository.deleteAll(); }
    @Override public List<UserTicket> findAllByOrderByCheckedAtDesc() { return repository.findAllByOrderByCheckedAtDesc() .stream().map(mapper::toDomain).toList(); }
}
