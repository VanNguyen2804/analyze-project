package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.*;
import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.domain.service.DrawQuality;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Same development replay can run unchanged against both revisions; never writes a ledger. */
class AlgorithmRevisionReportTest {
    @Test
    @EnabledIfSystemProperty(named="algorithm.revision.report", matches=".+")
    @SuppressWarnings("unchecked")
    void replayAllScorersWithFrozenDefaults() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/data/lottery_numbers.json")) { bytes = input.readAllBytes(); }
        List<LotteryNumber> raw = json.readValue(bytes, new TypeReference<>() {});
        var service = new AnalyzeService(mock(LotteryNumberRepository.class), mock(UserTicketRepository.class),
                mock(AlgorithmHyperparameterRepository.class), null);
        List<Map<String,Object>> categories = new ArrayList<>();
        for (String category : List.of("MEGA", "POWER")) {
            var rows = DrawQuality.history(raw).stream().filter(r -> category.equals(r.getCategory()))
                    .sorted(Comparator.comparing(LotteryNumber::getDrawDate)).toList();
            List<Map<String,Object>> measured = new ArrayList<>();
            Map<String,String> versions = new LinkedHashMap<>();
            int max = category.equals("POWER") ? 55 : 45;
            for (int i = 10; i < rows.size(); i++) {
                var target = rows.get(i); var history = rows.subList(0,i);
                assertTrue(history.stream().allMatch(r -> r.getDrawDate().isBefore(target.getDrawDate())));
                var temporal = service.analyzeAndPredictWithConfiguration(category,"temporal_learning","bayesian_graph",history,null);
                var stack = service.analyzeAndPredictWithConfiguration(category,"deep_stacking","bayesian_graph",history,null);
                var methods = new LinkedHashMap<String,Object>();
                for (var item : List.of(temporal.getPrimaryAlgorithmResult(), temporal.getSecondaryAlgorithmResult(), stack.getPrimaryAlgorithmResult())) {
                    String id = (String)item.get("id");
                    versions.put(id, Objects.toString(item.get("modelVersion"),"legacy-source-"+id));
                    var ticket = (List<Integer>)item.get("numbers");
                    assertEquals(6,new HashSet<>(ticket).size());
                    assertTrue(ticket.stream().allMatch(n -> n >= 1 && n <= max));
                    methods.put(id, metrics(ticket,target));
                }
                versions.put("temporal_learning",temporal.getModelVersion()); versions.put("deep_stacking",stack.getModelVersion());
                Map<Integer,Long> counts = new HashMap<>();
                for (int n=1;n<=max;n++) { int number=n; counts.put(n,history.stream().filter(r->r.getNumbers().contains(number)).count()); }
                var frequency = counts.keySet().stream().sorted(Comparator.<Integer>comparingLong(counts::get).reversed().thenComparingInt(n->n)).toList();
                methods.put("frequency", metrics(frequency.subList(0,6),target));
                double randomMean=0, randomHit3=0;
                for (int replicate=0;replicate<100;replicate++) {
                    var pool = new ArrayList<>(frequency);
                    Collections.shuffle(pool,new Random(("revision-replay-v1|"+category+"|"+target.getDrawDate()+"|"+replicate).hashCode()));
                    var outcome = metrics(pool.subList(0,6),target);
                    randomMean += ((Number)outcome.get("matched")).intValue();
                    if(Boolean.TRUE.equals(outcome.get("hit3"))) randomHit3++;
                }
                methods.put("uniform100",Map.of("matched",randomMean/100,"hit3Rate",randomHit3/100));
                measured.add(Map.of("date",target.getDrawDate().toString(),"trainingThrough",history.get(history.size()-1).getDrawDate().toString(),"methods",methods));
            }
            Map<String,Object> summaries = new LinkedHashMap<>();
            for(String method:List.of("temporal_learning","bayesian_graph","deep_stacking","frequency","uniform100")) {
                var values = measured.stream().map(m -> (Map<String,Object>)((Map<?,?>)m.get("methods")).get(method)).toList();
                summaries.put(method,Map.of("draws",values.size(),"averageMatched",values.stream().mapToDouble(v->((Number)v.get("matched")).doubleValue()).average().orElse(0),
                        "hit3Rate",values.stream().mapToDouble(v->method.equals("uniform100")?((Number)v.get("hit3Rate")).doubleValue():Boolean.TRUE.equals(v.get("hit3"))?1:0).average().orElse(0)));
            }
            categories.add(Map.of("category",category,"eligibleDraws",rows.size(),"versions",versions,"summary",summaries,"byDraw",measured));
        }
        var report=Map.of("protocol","revision-replay-v1","warmupDraws",10,"ticketBudget",1,"uniformReplicates",100,
                "configuration","frozen code defaults; no parameter selection","inputSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                "limitations","Previously inspected repository development history, not an untouched test or prospective forecasts. No tuning from this report. One ticket per model/category/draw; random replicates are not independent draws.","categories",categories);
        var output=Path.of(System.getProperty("algorithm.revision.report"));
        Files.createDirectories(output.toAbsolutePath().getParent()); json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);
    }

    private Map<String,Object> metrics(List<Integer> ticket, LotteryNumber draw) {
        long matched=ticket.stream().filter(draw.getNumbers()::contains).count();
        return Map.of("numbers",ticket,"matched",matched,"hit3",matched>=3);
    }
}
