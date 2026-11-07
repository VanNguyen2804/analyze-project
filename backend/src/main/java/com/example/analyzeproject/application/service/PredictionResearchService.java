package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.*;
import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.domain.service.DrawQuality;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import static com.example.analyzeproject.application.service.ForecastProtocol.map;

/** Runtime inventory and explicitly requested research; GET never runs models or writes ledgers. */
public final class PredictionResearchService {
    private final LotteryNumberRepository draws;
    private final ForecastStore forecasts;
    private final AiLabStore lab;
    private final Clock clock;
    private final PredictionBenchmark benchmark;
    private final ReentrantLock studyLock = new ReentrantLock();
    public PredictionResearchService(LotteryNumberRepository draws, ForecastStore forecasts, AiLabStore lab, Clock clock, AnalyzeService analyze) {
        this.draws = draws; this.forecasts = forecasts; this.lab = lab; this.clock = clock; this.benchmark = new PredictionBenchmark(analyze);
    }
    public Map<String,Object> inventory(String category) {
        validate(category); var now = clock.instant(); var raw = draws.findAllByOrderByDrawDateDescCreatedAtDesc();
        var expected = ForecastProtocol.expected(category, now);
        var history = ForecastProtocol.training(raw, category, now);
        var dates = new HashSet<>(history.stream().map(LotteryNumber::getDrawDate).toList());
        var missing = new ArrayList<String>();
        LocalDate first = dates.stream().min(LocalDate::compareTo).orElse(null);
        if (first != null) for (LocalDate date = first; !date.isAfter(expected); date = date.plusDays(1))
            if (ForecastProtocol.target(category, date.atStartOfDay(ForecastProtocol.ZONE).toInstant()).equals(date) && !dates.contains(date)) missing.add(date.toString());
        int rows = 0, invalid = 0, future = 0; Map<String,Integer> quality = new TreeMap<>();
        for (var row : raw) if (row != null && category.equals(row.getCategory())) {
            rows++; if (!DrawQuality.eligible(row)) invalid++;
            if (row.getDrawDate() != null && row.getDrawDate().isAfter(expected)) future++;
            quality.merge(Objects.toString(row.getQuality() == null ? null : row.getQuality().get("status"), "UNVERIFIED"), 1, Integer::sum);
        }
        String through = history.isEmpty() ? null : history.get(history.size() - 1).getDrawDate().toString();
        var main = ledgerInventory(forecasts.readLedger(), category, history, now,1);
        var labs = ledgerInventory(lab.ledger(), category, history, now,5);
        return map("asOf", now.toString(), "source", "runtime_repository", "category", category, "totalRows", rows,
                "eligibleDraws", history.size(), "ineligibleRows", invalid, "futureOrNotYetAcceptedRows", future,
                "supersededEligibleRows", Math.max(0, rows - invalid - future - history.size()),
                "fromDate", first == null ? null : first.toString(), "trainingThrough", through, "expectedThrough", expected.toString(),
                "targetDate", ForecastProtocol.target(category, now).toString(), "fresh", dates.contains(expected),
                "missingDrawDates", missing, "qualityCounts", quality, "historyHash", ForecastProtocol.hash(history.stream().map(DrawQuality::fingerprint).toList()),
                "forecasts", main, "lab", labs, "evidenceType", "PROSPECTIVE_FROZEN",
                "sourceNote", "SOURCE_MATCHED là khớp nguồn đã cấu hình, chưa phải xác minh độc lập với Vietlott.");
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> ledgerInventory(Map<String,Object> ledger, String category, List<LotteryNumber> history, Instant now,int budget) {
        var snapshots = ((List<Map<String,Object>>)ledger.getOrDefault("forecasts", List.of())).stream().filter(f -> category.equals(f.get("category"))).toList();
        var latest = new HashMap<Object,Map<String,Object>>();
        ((List<Map<String,Object>>)ledger.getOrDefault("settlements", List.of())).forEach(e -> latest.put(e.get("forecastId"), e));
        Map<String,LotteryNumber> actual = new HashMap<>(); history.forEach(r -> actual.put(r.getDrawDate().toString(), r));
        Map<String,Set<Object>> cohorts = new TreeMap<>(); Set<Object> prospectiveDates = new HashSet<>(), settledDates = new HashSet<>(); int valid = 0;
        Map<String,List<Map<String,Object>>> batches=new TreeMap<>();
        for (var snapshot : snapshots) if (ForecastProtocol.isProspective(snapshot)) {
            valid++; prospectiveDates.add(snapshot.get("targetDate"));
            var event = latest.get(snapshot.get("id")); var result = actual.get(snapshot.get("targetDate"));
            if (event != null && result != null && "SETTLED".equals(event.get("status"))
                    && !now.isBefore(result.getDrawDate().atTime(19, 0).atZone(ForecastProtocol.ZONE).toInstant())
                    && DrawQuality.fingerprint(result).equals(event.get("resultFingerprint"))) {
                String cohort = snapshot.getOrDefault("labModel",snapshot.get("algorithm")) + "|" + snapshot.get("modelVersion") + "|" + snapshot.get("codeVersion") + "|" + snapshot.get("configurationHash");
                batches.computeIfAbsent(cohort+"|"+snapshot.get("targetDate"), k->new ArrayList<>()).add(snapshot);
            }
        }
        for(var entry:batches.entrySet()) {
            var batch=entry.getValue();
            if(budget==5 ? !AiLabService.completeBatch(batch) : batch.size()!=1) continue;
            Object date=batch.get(0).get("targetDate");settledDates.add(date);
            String cohort=entry.getKey().substring(0,entry.getKey().lastIndexOf('|'));
            cohorts.computeIfAbsent(cohort,k->new HashSet<>()).add(date);
        }
        return map("savedTickets", snapshots.size(), "validProspectiveTickets", valid, "excludedTickets", snapshots.size() - valid,
                "savedDraws", prospectiveDates.size(), "settledDraws", settledDates.size(), "cohorts", cohorts.entrySet().stream()
                        .map(e -> map("cohort", e.getKey(), "evaluatedDraws", e.getValue().size())).toList());
    }
    public Map<String,Object> benchmark(String category, PredictionBenchmark.Options options) {
        validate(category);
        if (!studyLock.tryLock()) return map("status", "BUSY");
        try {
            var history = ForecastProtocol.training(draws.findAllByOrderByDrawDateDescCreatedAtDesc(), category, clock.instant());
            return benchmark.run(history, category, options);
        } finally { studyLock.unlock(); }
    }
    private static void validate(String category) {
        if (!Set.of("MEGA", "POWER").contains(category)) throw new IllegalArgumentException("Invalid category");
    }
}
