package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.PredictionResponseDto;
import com.example.analyzeproject.model.LotteryNumber;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import static com.example.analyzeproject.service.ForecastProtocol.map;

/** Frozen three-ticket, MEGA-only experiment. Never reconstructs past predictions. */
public final class MegaPortfolioProtocol {
    public static final String VERSION = "mega-portfolio-v1";
    public static final int BUDGET = 3;
    public static final List<String> ARMS = List.of("portfolio", "wheel", "frequency", "random");
    private MegaPortfolioProtocol() {}

    public static String label(String arm) {
        return switch (arm) {
            case "portfolio" -> "Giảm trùng vé";
            case "wheel" -> "Cách ghép cũ";
            case "frequency" -> "Tần suất + giảm trùng";
            case "random" -> "Ngẫu nhiên";
            default -> throw new IllegalArgumentException("Nhóm thử nghiệm không hợp lệ.");
        };
    }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> prepare(List<LotteryNumber> history, PredictionResponseDto prediction, String codeVersion, Instant now) {
        history = ForecastProtocol.training(history,"MEGA",now);
        if (!"SUCCESS".equals(prediction.getStatus()) || !"MEGA".equals(prediction.getCategory()))
            throw new IllegalStateException("Chưa có xếp hạng MEGA hợp lệ.");
        var ranking = ((List<Number>)prediction.getPrimaryAlgorithmResult().get("ranking")).stream().map(Number::intValue).toList();
        validateRanking(ranking);
        String target = ForecastProtocol.target("MEGA", now).toString();
        var configuration = map("experiment", VERSION, "algorithm", "temporal_learning", "scoring", "fixed_defaults",
                "budget", BUDGET, "selection", TicketPortfolio.VERSION, "warmupDraws", 10,
                "primaryMetric", "draw_with_ticket_matching_at_least_three", "randomSeed", VERSION+":MEGA:"+target);
        var snapshot = ForecastProtocol.prepare(history, "MEGA", VERSION, VERSION, codeVersion, configuration,
                ranking.subList(0,6), now);
        if (history.size()<10) throw new IllegalStateException("Cần ít nhất 10 kỳ lịch sử hợp lệ.");
        int[] counts = new int[46];
        history.forEach(r -> r.getNumbers().forEach(n -> counts[n]++));
        var frequencyRanking = IntStream.rangeClosed(1,45).boxed()
                .sorted(Comparator.<Integer>comparingInt(n -> counts[n]).reversed().thenComparingInt(n -> n)).toList();
        Random random = new Random((VERSION+":MEGA:"+target).hashCode());
        Set<List<Integer>> randomTickets = new LinkedHashSet<>();
        while (randomTickets.size()<BUDGET) {
            var pool = new ArrayList<>(IntStream.rangeClosed(1,45).boxed().toList());
            Collections.shuffle(pool,random);
            randomTickets.add(pool.subList(0,6).stream().sorted().toList());
        }
        var arms = map("portfolio", TicketPortfolio.select(ranking,45,BUDGET),
                "wheel", prediction.getTickets().subList(0,BUDGET),
                "frequency", TicketPortfolio.select(frequencyRanking,45,BUDGET), "random", new ArrayList<>(randomTickets));
        snapshot.put("ranking",ranking);
        snapshot.put("arms",arms);
        snapshot.put("payloadHash",ForecastProtocol.hash(map("ranking",ranking,"arms",arms)));
        if (!valid(snapshot)) throw new IllegalStateException("Bộ vé thử nghiệm không hợp lệ.");
        return snapshot;
    }

    private static void validateRanking(List<Integer> ranking) {
        if (ranking.size()!=45 || new HashSet<>(ranking).size()!=45 || ranking.stream().anyMatch(n -> n==null || n<1 || n>45))
            throw new IllegalArgumentException("Thiếu xếp hạng MEGA đầy đủ.");
    }

    @SuppressWarnings("unchecked")
    public static boolean valid(Map<String,Object> snapshot) {
        try {
            if (!"MEGA".equals(snapshot.get("category")) || !VERSION.equals(snapshot.get("modelVersion"))
                    || !ForecastProtocol.isProspective(snapshot)) return false;
            if (((List<?>)snapshot.get("history")).size()<10) return false;
            var ranking = ((List<Number>)snapshot.get("ranking")).stream().map(Number::intValue).toList();
            validateRanking(ranking);
            if (!ranking.subList(0,6).stream().sorted().toList().equals(snapshot.get("numbers"))) return false;
            var arms = (Map<String,List<List<Integer>>>)snapshot.get("arms");
            if (!arms.keySet().equals(new HashSet<>(ARMS))) return false;
            for (String arm:ARMS) {
                var tickets=arms.get(arm);
                if (tickets.size()!=BUDGET) return false;
                var normalized=tickets.stream().map(t -> WorkspaceProtocol.numbers("MEGA",t)).toList();
                if (new HashSet<>(normalized).size()!=BUDGET) return false;
            }
            return ForecastProtocol.hash(map("ranking",snapshot.get("ranking"),"arms",arms)).equals(snapshot.get("payloadHash"));
        } catch (RuntimeException e) { return false; }
    }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> settle(Map<String,Object> snapshot, List<LotteryNumber> draws, Instant now, Map<String,Object> previous) {
        var outcome=ForecastProtocol.settle(snapshot,draws,now,previous);
        var metrics=new LinkedHashMap<String,Object>();
        if ("SETTLED".equals(outcome.get("status"))) {
            var arms=(Map<String,List<List<Integer>>>)snapshot.get("arms");
            var actual=(List<Integer>)outcome.get("actualNumbers");
            for (String arm:ARMS) metrics.put(arm,WorkspaceProtocol.metrics(arms.get(arm),actual));
        }
        return map("forecastId",snapshot.get("id"),"checkedAt",now.toString(),"status",outcome.get("status"),
                "resultFingerprint",outcome.get("resultFingerprint"),"actualNumbers",outcome.get("actualNumbers"),"arms",metrics);
    }

    public static String cohort(Map<String,Object> snapshot) {
        // The random seed varies by target; keep it outside the policy hash used for cohorts.
        var policy=new LinkedHashMap<>((Map<?,?>)snapshot.get("configuration"));
        policy.remove("randomSeed");
        return snapshot.get("codeVersion")+"|"+ForecastProtocol.hash(policy);
    }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> row(Map<String,Object> snapshot, Map<String,Object> settlement) {
        var arms=(Map<String,List<List<Integer>>>)snapshot.get("arms");
        var metrics=settlement==null?Map.<String,Object>of():(Map<String,Object>)settlement.get("arms");
        return map("id",snapshot.get("id"),"targetDate",snapshot.get("targetDate"),"createdAt",snapshot.get("createdAt"),
                "trainingThrough",snapshot.get("trainingThrough"),"codeVersion",snapshot.get("codeVersion"),
                "status",settlement==null?"PENDING":settlement.get("status"),"actualNumbers",settlement==null?null:settlement.get("actualNumbers"),
                "arms",ARMS.stream().map(arm -> {
                    var diagnostic=TicketPortfolio.diagnostics(arms.get(arm));
                    return map("id",arm,"label",label(arm),"tickets",arms.get(arm),"uniqueNumbers",diagnostic.get("uniqueNumbers"),
                            "maxPairOverlap",diagnostic.get("maxPairOverlap"),"metrics",metrics.get(arm));
                }).toList());
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String,Object>> evaluate(List<Map<String,Object>> snapshots, Map<Object,Map<String,Object>> latest) {
        Map<String,List<Map<String,Object>>> groups=new LinkedHashMap<>();
        snapshots.stream().filter(MegaPortfolioProtocol::valid).forEach(f -> groups.computeIfAbsent(cohort(f),k -> new ArrayList<>()).add(f));
        return groups.entrySet().stream().map(group -> {
            var entries=group.getValue();
            var measured=entries.stream().map(f -> latest.get(f.get("id"))).filter(Objects::nonNull)
                    .filter(e -> "SETTLED".equals(e.get("status"))).toList();
            var summaries=ARMS.stream().map(arm -> {
                var metrics=measured.stream().map(e -> (Map<String,Object>)((Map<?,?>)e.get("arms")).get(arm)).toList();
                long hits=metrics.stream().filter(m -> Boolean.TRUE.equals(m.get("hit3"))).count();
                return map("id",arm,"label",label(arm),"hit3Draws",hits,"hit3Percent",metrics.isEmpty()?null:100.0*hits/metrics.size(),
                        "averageMatched",average(metrics,"averageMatched"),"averageResultCoverage",average(metrics,"coverageCount"));
            }).toList();
            return map("cohort",group.getKey(),"codeVersion",entries.get(0).get("codeVersion"),"savedDraws",entries.size(),"evaluatedDraws",measured.size(),
                    "firstTargetDate",entries.stream().map(f -> (String)f.get("targetDate")).min(String::compareTo).orElseThrow(),
                    "lastTargetDate",entries.stream().map(f -> (String)f.get("targetDate")).max(String::compareTo).orElseThrow(),
                    "arms",summaries,"status","COLLECTING");
        }).toList();
    }

    private static Double average(List<Map<String,Object>> metrics,String key) {
        return metrics.isEmpty()?null:metrics.stream().mapToDouble(m -> ((Number)m.get(key)).doubleValue()).average().orElseThrow();
    }
}
