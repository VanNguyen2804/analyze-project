package com.example.analyzeproject.service;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit offline development evaluation; never reads or modifies the live database. */
class PortfolioBacktestTest {
    @Test
    @EnabledIfSystemProperty(named = "portfolio.report", matches = ".+")
    void writeWalkForwardReport() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        byte[] input = getClass().getResourceAsStream("/data/lottery_numbers.json").readAllBytes();
        List<LotteryNumber> raw = mapper.readValue(input, new TypeReference<>() {});
        var service = new AnalyzeService(mock(LotteryNumberRepository.class), mock(UserTicketRepository.class),
                mock(AlgorithmHyperparameterRepository.class), null);
        var groups = new ArrayList<Map<String,Object>>();
        for (String category : List.of("POWER", "MEGA")) {
            var rows = DrawQuality.history(raw).stream().filter(r -> category.equals(r.getCategory()))
                    .filter(r -> r.getDrawDate().isBefore(LocalDate.of(2026,10,27)))
                    .sorted(Comparator.comparing(LotteryNumber::getDrawDate)).toList();
            var evaluations = new ArrayList<Map<String,Object>>();
            for (int i = 10; i < rows.size(); i++) {
                var target = rows.get(i);
                var history = rows.subList(0, i);
                assertTrue(history.stream().allMatch(r -> r.getDrawDate().isBefore(target.getDrawDate())));
                var response = service.analyzeAndPredict(category, "temporal_learning", "bayesian_graph", history);
                assertEquals("SUCCESS", response.getStatus());
                var ten = response.getNumbers().stream().sorted().toList();
                // Reproduce the first three wheels of the previous generator exactly.
                int[][] wheels = {{0,1,2,3,4,5},{0,1,4,5,6,7},{0,2,4,6,7,8}};
                var legacy = new ArrayList<List<Integer>>();
                for (int[] wheel : wheels) legacy.add(Arrays.stream(wheel).mapToObj(ten::get).toList());
                int max = category.equals("POWER") ? 55 : 45;
                List<Integer> frequency = new ArrayList<>();
                for(int n=1;n<=max;n++) frequency.add(n);
                frequency.sort(Comparator.<Integer>comparingLong(n -> history.stream().filter(r -> r.getNumbers().contains(n)).count())
                        .reversed().thenComparingInt(n -> n));
                // Uniform distinct tickets; fixed seed per category/date, never selected by outcome.
                var random = new Random((category+target.getDrawDate()).hashCode());
                var randomTickets = new LinkedHashSet<List<Integer>>();
                while(randomTickets.size()<3) {
                    List<Integer> shuffled = new ArrayList<>(frequency); Collections.shuffle(shuffled, random);
                    randomTickets.add(shuffled.subList(0,6).stream().sorted().toList());
                }
                var comparison = new LinkedHashMap<String,Object>();
                comparison.put("date", target.getDrawDate().toString());
                comparison.put("legacy", metrics(legacy, target));
                @SuppressWarnings("unchecked")
                var diversified = (List<List<Integer>>) response.getRecommendations().get("diversifiedTickets");
                comparison.put("portfolio", metrics(diversified.subList(0,3), target));
                comparison.put("frequencyPortfolio", metrics(TicketPortfolio.select(frequency,max,3), target));
                comparison.put("uniform", metrics(new ArrayList<>(randomTickets), target));
                evaluations.add(comparison);
            }
            var summaries = new LinkedHashMap<String,Object>();
            for(String method : List.of("legacy","portfolio","frequencyPortfolio","uniform")) {
                double totalHits=0, wins=0, coverage=0;
                for(var row : evaluations) {
                    var m = (Map<?,?>)row.get(method);
                    totalHits += ((Number)m.get("totalMatches")).doubleValue();
                    wins += ((Number)m.get("anyTicketAtLeast3")).doubleValue();
                    coverage += ((Number)m.get("uniqueNumbers")).doubleValue();
                }
                int count=evaluations.size();
                summaries.put(method, Map.of("draws",count,"meanMatchesPerTicket",count==0?0:totalHits/(3*count),
                        "drawsWithAtLeast3",wins,"meanUniqueNumbers",count==0?0:coverage/count));
            }
            groups.add(Map.of("category",category,"eligibleDraws",rows.size(),"summaries",summaries,"byDraw",evaluations));
        }
        var report = Map.of("version",TicketPortfolio.VERSION,"budget",3,"warmupDraws",10,
                "inputSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)),
                "excludedFromEvaluation","2026-10-27 and later",
                "limitations","Development replay on previously inspected repository history; not an untouched test or proof of predictive advantage. No tuning on this report. Uniform baseline is one seeded portfolio per draw. Small sample; future validation required.",
                "groups",groups);
        Path output = Path.of(System.getProperty("portfolio.report"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);
    }

    private Map<String,Object> metrics(List<List<Integer>> tickets, LotteryNumber target) {
        int total=0, best=0;
        for(var ticket:tickets) { int hit=TicketPortfolio.overlap(ticket,target.getNumbers()); total+=hit; best=Math.max(best,hit); }
        return Map.of("totalMatches",total,"anyTicketAtLeast3",best>=3?1:0,
                "uniqueNumbers",tickets.stream().flatMap(Collection::stream).distinct().count());
    }
}
