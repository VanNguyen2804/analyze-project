package com.example.analyzeproject.sync;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.LotteryNumberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LotterySyncTest {
    private final LotterySyncProperties properties = new LotterySyncProperties();
    private final LotteryFeedClient client = new LotteryFeedClient(new ObjectMapper(), properties);
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-10-25T10:00:00+07:00");
    private static final LocalDate FROM = LocalDate.parse("2026-10-01");
    private static final String POWER = "{\"date\":\"2026-10-24\",\"id\":\"01415\",\"result\":[6,7,18,41,44,50,53]}";
    private static final String MEGA = "{\"date\":\"2026-10-23\",\"id\":\"01579\",\"result\":[2,5,11,22,32,40]}";

    @Test void parsesPublicSchemaAndKeepsSpecialSeparate() throws Exception {
        var draws = client.parse(POWER + "\n" + POWER, "POWER", FROM, NOW);
        assertEquals(1, draws.size());
        assertEquals(List.of(6,7,18,41,44,50), draws.get(0).numbers());
        assertEquals(53, draws.get(0).special());
        assertEquals("01415", draws.get(0).drawId());
        assertNull(client.parse(MEGA, "MEGA", FROM, NOW).get(0).special());
    }

    @Test void rejectsInvalidOrAmbiguousFeedBeforeAnyWrites() {
        for (String input : List.of(POWER.replace("53", "50"), POWER.replace("53", "56"),
                POWER.replace(",53", ""), POWER.replace("18", "18.5"), POWER.replace("2026-10-24", "2026-02-30"),
                POWER + "\n" + POWER.replace("53", "54"), "<html>error</html>", "")) {
            assertThrows(IOException.class, () -> client.parse(input, "POWER", FROM, NOW));
        }
    }

    @Test void vietnamCutoffAndFutureDates() throws Exception {
        String today = MEGA.replace("2026-10-23", "2026-10-25").replace("01579", "01580");
        assertEquals(1, client.parse(MEGA + "\n" + today, "MEGA", FROM, NOW).size());
        var published = NOW.withHour(18).withMinute(30);
        assertEquals(2, client.parse(MEGA + "\n" + today, "MEGA", FROM, published).size());
        assertEquals(LocalDate.parse("2026-10-23"), LotteryFeedClient.latestExpected("MEGA", NOW));
        assertEquals(LocalDate.parse("2026-10-25"), LotteryFeedClient.latestExpected("MEGA", published));
        assertEquals(LocalDate.parse("2026-10-24"), LotteryFeedClient.latestExpected("POWER", published));
    }

    @Test void importerIsIdempotentAndPreservesConflictingLocalResult() throws Exception {
        var repository = mock(LotteryNumberRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var importer = new LotteryResultImporter(repository, jdbc);
        var draws = client.parse(POWER, "POWER", FROM, NOW);
        when(repository.findByDrawDateAndCategoryOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        assertEquals(1, importer.importDraws(draws, "https://example.com/power").inserted());
        verify(jdbc).execute("SELECT pg_advisory_xact_lock(76452655)");
        verify(repository).save(argThat(row -> row.getNote().contains("01415") && row.getSpecialNumber() == 53));
        var existing = new LotteryNumber(draws.get(0).date(), "POWER", new ArrayList<>(draws.get(0).numbers()), 53, "manual");
        when(repository.findByDrawDateAndCategoryOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(existing));
        assertEquals(1, importer.importDraws(draws, "https://example.com/power").unchanged());
        existing.setSpecialNumber(54);
        var conflict = importer.importDraws(draws, "https://example.com/power");
        assertEquals(1, conflict.conflicts().size());
        assertEquals(54, existing.getSpecialNumber());
        verify(repository, times(1)).save(any());
    }

    @Test void schedulerIsolatesFailuresRetriesAndReportsStaleSources() throws Exception {
        var remote = mock(LotteryFeedClient.class);
        var importer = mock(LotteryResultImporter.class);
        var scheduler = new LotteryResultScheduler(properties, remote, importer);
        var powerDraws = client.parse(POWER, "POWER", FROM, NOW);
        var megaDraws = client.parse(MEGA, "MEGA", FROM, NOW);
        when(remote.fetch(eq("MEGA"), any(), any())).thenThrow(new IOException("HTTP 503")).thenReturn(megaDraws);
        when(remote.fetch(eq("POWER"), any(), any())).thenReturn(powerDraws);
        when(importer.importDraws(any(), any())).thenReturn(new LotteryResultImporter.ImportResult(1, 0, List.of()));
        scheduler.runAt(NOW);
        assertEquals("ERROR", categoryStatus(scheduler, "MEGA").get("status"));
        assertEquals("SUCCESS", categoryStatus(scheduler, "POWER").get("status"));
        scheduler.runAt(NOW.plusDays(1));
        assertEquals("STALE", categoryStatus(scheduler, "MEGA").get("status"));
        assertEquals(true, categoryStatus(scheduler, "MEGA").get("sourceStale"));
        properties.setEnabled(false);
        scheduler.sync();
        verify(remote, times(2)).fetch(eq("MEGA"), any(), any());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> categoryStatus(LotteryResultScheduler scheduler, String category) {
        var run = (Map<String, Object>) scheduler.status().get("lastRun");
        return (Map<String, Object>) ((Map<String, Object>) run.get("categories")).get(category);
    }
}
