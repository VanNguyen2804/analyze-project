package com.example.analyzeproject.sync;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.LotteryNumberRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class LotteryResultImporter {
    private final LotteryNumberRepository repository;
    private final JdbcTemplate jdbc;

    public LotteryResultImporter(LotteryNumberRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    public record Conflict(LocalDate date, List<Integer> stored, Integer storedSpecial,
                           List<Integer> incoming, Integer incomingSpecial) {}
    public record ImportResult(int inserted, int unchanged, List<Conflict> conflicts) {}

    @Transactional
    public ImportResult importDraws(List<LotteryFeedClient.Draw> draws, String sourceUrl) {
        // Serialize scheduler writers across replicas; the lock is released on commit/rollback.
        jdbc.execute("SELECT pg_advisory_xact_lock(76452655)");
        int inserted = 0, unchanged = 0;
        List<Conflict> conflicts = new ArrayList<>();
        for (var draw : draws) {
            var existing = repository.findByDrawDateAndCategoryOrderByCreatedAtDesc(draw.date(), draw.category());
            if (!existing.isEmpty()) {
                var current = existing.get(0);
                if (new HashSet<>(current.getNumbers()).equals(new HashSet<>(draw.numbers()))
                        && Objects.equals(current.getSpecialNumber(), draw.special())) unchanged++;
                else conflicts.add(new Conflict(draw.date(), List.copyOf(current.getNumbers()), current.getSpecialNumber(), draw.numbers(), draw.special()));
                continue;
            }
            repository.save(new LotteryNumber(draw.date(), draw.category(), new ArrayList<>(draw.numbers()), draw.special(),
                    "Imported from " + sourceUrl + " | draw #" + draw.drawId() + " | community mirror of vietlott.vn"));
            inserted++;
        }
        return new ImportResult(inserted, unchanged, List.copyOf(conflicts));
    }
}
