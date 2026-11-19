package com.example.analyzeproject.infrastructure.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in check against the real official history, without writing to a database. */
@EnabledIfSystemProperty(named = "lottery.live", matches = "true")
class LotteryLiveSyncTest {
    @Test void officialHistoryContainsNovember17() throws Exception {
        var client = new LotteryFeedClient(new ObjectMapper(), new LotterySyncProperties());
        var draws = client.fetch("POWER", LocalDate.of(2026, 8, 21),
                ZonedDateTime.of(2026, 11, 19, 22, 0, 0, 0, LotteryFeedClient.ZONE));
        assertTrue(draws.stream().anyMatch(draw -> draw.date().equals(LocalDate.of(2026, 11, 17))),
                "Official history must include 17/11/2026");
        System.out.println("Official POWER dates: " + draws.stream().map(draw -> draw.date()).toList());
    }
}
