package com.example.analyzeproject.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
public class LotteryResultScheduler {
    private static final Logger log = LoggerFactory.getLogger(LotteryResultScheduler.class);
    private final LotterySyncProperties properties;
    private final LotteryFeedClient client;
    private final LotteryResultImporter importer;
    private volatile Map<String, Object> lastRun = Map.of("status", "NOT_RUN");

    public LotteryResultScheduler(LotterySyncProperties properties, LotteryFeedClient client, LotteryResultImporter importer) {
        this.properties = properties; this.client = client; this.importer = importer;
    }

    @Scheduled(fixedDelayString = "${lottery.sync.interval-seconds:900}",
            initialDelayString = "${lottery.sync.startup-delay-seconds:10}", timeUnit = TimeUnit.SECONDS)
    public void sync() {
        if (!properties.isEnabled()) return;
        runAt(ZonedDateTime.now(LotteryFeedClient.ZONE));
    }

    synchronized void runAt(ZonedDateTime now) {
        var from = now.withZoneSameInstant(LotteryFeedClient.ZONE).toLocalDate().minusDays(properties.getLookbackDays());
        var categories = new LinkedHashMap<String, Object>();
        for (String category : List.of("MEGA", "POWER")) {
            var detail = new LinkedHashMap<String, Object>();
            detail.put("sourceUrl", properties.sourceUrl(category));
            try {
                var draws = client.fetch(category, from, now);
                var imported = importer.importDraws(draws, properties.sourceUrl(category));
                LocalDate latest = draws.get(draws.size() - 1).date();
                LocalDate expected = LotteryFeedClient.latestExpected(category, now);
                detail.put("status", imported.conflicts().isEmpty() ? (latest.isBefore(expected) ? "STALE" : "SUCCESS") : "CONFLICT");
                detail.put("latestSourceDate", latest);
                detail.put("expectedLatestDate", expected);
                detail.put("sourceStale", latest.isBefore(expected));
                detail.put("inserted", imported.inserted());
                detail.put("unchanged", imported.unchanged());
                detail.put("conflicts", imported.conflicts());
                log.info("Lottery sync {}: {} inserted={}, unchanged={}, conflicts={}", category, detail.get("status"), imported.inserted(), imported.unchanged(), imported.conflicts().size());
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                detail.put("status", "ERROR");
                detail.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
                log.warn("Lottery sync {} failed; retry on next scheduled run: {}", category, ex.getMessage());
            }
            categories.put(category, Collections.unmodifiableMap(detail));
        }
        lastRun = Map.of("checkedAt", now.toInstant(), "fromDate", from, "categories", Collections.unmodifiableMap(categories));
    }

    public Map<String, Object> status() {
        return Map.of("enabled", properties.isEnabled(), "intervalSeconds", properties.getIntervalSeconds(),
                "timezone", LotteryFeedClient.ZONE.toString(), "lookbackDays", properties.getLookbackDays(), "lastRun", lastRun);
    }
}
