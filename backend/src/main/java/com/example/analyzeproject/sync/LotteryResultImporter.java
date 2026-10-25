package com.example.analyzeproject.sync;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.LotteryNumberRepository;
import com.example.analyzeproject.service.DrawQuality;
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
    public record ImportResult(int inserted, int unchanged, List<Conflict> conflicts, int updated, int corrected, List<Map<String,Object>> quarantined) {}

    @Transactional
    public ImportResult importDraws(List<LotteryFeedClient.Draw> draws, String sourceUrl) {
        // Serialize scheduler writers across replicas; the lock is released on commit/rollback.
        jdbc.execute("SELECT pg_advisory_xact_lock(76452655)");
        int inserted = 0, unchanged = 0, updated = 0, corrected = 0;
        List<Conflict> conflicts = new ArrayList<>();
        List<Map<String,Object>> quarantined = new ArrayList<>();
        for (var draw : draws) {
            var incoming = new LotteryNumber(draw.date(), draw.category(), new ArrayList<>(draw.numbers()), draw.special(), null);
            var existing = repository.findByDrawDateAndCategoryOrderByCreatedAtDesc(draw.date(), draw.category());
            if (!existing.isEmpty()) {
                var current = existing.get(0);
                var before = DrawQuality.fingerprint(current);
                if (DrawQuality.reconcile(current, incoming, sourceUrl, draw.drawId(), null)) { repository.save(current); updated++; }
                if (!before.equals(DrawQuality.fingerprint(current))) corrected++;
                if (DrawQuality.eligible(current)) { if (before.equals(DrawQuality.fingerprint(current))) unchanged++; }
                else {
                    conflicts.add(new Conflict(draw.date(), new ArrayList<>(current.getNumbers() == null ? List.of() : current.getNumbers()), current.getSpecialNumber(), draw.numbers(), draw.special()));
                    quarantined.add(Map.of("id",current.getId(),"date",current.getDrawDate(),"reason","SOURCE_CONFLICT"));
                }
                for (var duplicate : existing.subList(1, existing.size())) {
                    if (DrawQuality.reconcile(duplicate, null, sourceUrl, null, "SUPERSEDED_REVISION")) { repository.save(duplicate); updated++; }
                    quarantined.add(Map.of("id",duplicate.getId(),"date",duplicate.getDrawDate(),"reason","SUPERSEDED_REVISION"));
                }
                continue;
            }
            incoming.setNote("Imported from " + sourceUrl + " | draw #" + draw.drawId() + " | community mirror of vietlott.vn");
            DrawQuality.reconcile(incoming, incoming, sourceUrl, draw.drawId(), null);
            repository.save(incoming);
            inserted++;
        }
        if (!draws.isEmpty()) {
            var dates = new TreeSet<LocalDate>(); draws.forEach(d -> dates.add(d.date()));
            for (var row : repository.findByCategoryOrderByDrawDateDescCreatedAtDesc(draws.get(0).category())) {
                if (!row.getDrawDate().isBefore(dates.first()) && !row.getDrawDate().isAfter(dates.last()) && !dates.contains(row.getDrawDate())) {
                    if (DrawQuality.reconcile(row, null, sourceUrl, null, null)) { repository.save(row); updated++; }
                    quarantined.add(Map.of("id",row.getId(),"date",row.getDrawDate(),"reason","SOURCE_NOT_FOUND"));
                }
            }
        }
        return new ImportResult(inserted, unchanged, List.copyOf(conflicts), updated, corrected, quarantined);
    }
}
