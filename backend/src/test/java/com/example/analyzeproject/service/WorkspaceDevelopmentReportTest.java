package com.example.analyzeproject.service;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.example.analyzeproject.service.ForecastProtocol.map;

/** Explicit development replay, never writes the live database or changes defaults. */
class WorkspaceDevelopmentReportTest {
    @Test @EnabledIfSystemProperty(named="workspace.report.input",matches=".+")
    @SuppressWarnings("unchecked")
    void reportFixedFusionWeightsWithEqualBudgetControls() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();
        byte[] input=Files.readAllBytes(Path.of(System.getProperty("workspace.report.input")));
        List<LotteryNumber> raw=mapper.readValue(input,new TypeReference<>(){});
        var service=new AnalyzeService(mock(LotteryNumberRepository.class),mock(UserTicketRepository.class),mock(AlgorithmHyperparameterRepository.class),null);
        var categories=new ArrayList<Map<String,Object>>();
        for(String category:List.of("MEGA","POWER")) {
            var rows=DrawQuality.history(raw).stream().filter(r->category.equals(r.getCategory())).sorted(Comparator.comparing(LotteryNumber::getDrawDate)).toList();
            var measured=new ArrayList<Map<String,Object>>();
            for(int i=10;i<rows.size();i++) {
                var target=rows.get(i); var history=rows.subList(0,i);
                if(!WorkspaceProtocol.ready(history,category,target.getDrawDate())) continue;
                assertTrue(history.stream().allMatch(r->r.getDrawDate().isBefore(target.getDrawDate())));
                var prediction=service.analyzeAndPredictWithConfiguration(category,"temporal_learning","bayesian_graph",history,null);
                assertEquals("SUCCESS",prediction.getStatus());
                var first=(List<Integer>)prediction.getPrimaryAlgorithmResult().get("ranking"); var second=(List<Integer>)prediction.getSecondaryAlgorithmResult().get("ranking");
                var methods=new LinkedHashMap<String,Object>();
                for(double weight:List.of(.25,.5,.75)) {
                    var ticket=WorkspaceProtocol.combine(category,first,second,weight,List.of(),List.of());
                    methods.put("combined-"+weight,WorkspaceProtocol.metrics(List.of(ticket),target.getNumbers()));
                    var portfolio=new LinkedHashSet<List<Integer>>(); portfolio.add((List<Integer>)prediction.getPrimaryAlgorithmResult().get("numbers")); portfolio.add((List<Integer>)prediction.getSecondaryAlgorithmResult().get("numbers")); portfolio.add(ticket);
                    int budget=portfolio.size(); String seed=category+":"+target.getDrawDate()+":"+budget;
                    methods.put("portfolio-"+weight,map("user",WorkspaceProtocol.metrics(new ArrayList<>(portfolio),target.getNumbers()),
                            "frequency",WorkspaceProtocol.metrics(WorkspaceProtocol.controls(history,category,budget,seed,true),target.getNumbers()),
                            "random",WorkspaceProtocol.metrics(WorkspaceProtocol.controls(history,category,budget,seed,false),target.getNumbers())));
                }
                measured.add(map("date",target.getDrawDate().toString(),"trainingThrough",history.get(history.size()-1).getDrawDate().toString(),"methods",methods));
            }
            var summary=new ArrayList<Map<String,Object>>();
            for(double weight:List.of(.25,.5,.75)) for(String group:List.of("user","frequency","random")) {
                List<Map<String,Object>> metrics=measured.stream().map(m->(Map<String,Object>)((Map<String,Object>)((Map<String,Object>)m.get("methods")).get("portfolio-"+weight)).get(group)).toList();
                summary.add(map("weight",weight,"group",group,"evaluatedDraws",metrics.size(),"meanDrawAverageMatched",metrics.stream().mapToDouble(m->((Number)m.get("averageMatched")).doubleValue()).average().orElse(0),
                        "drawsHit3",metrics.stream().filter(m->Boolean.TRUE.equals(m.get("hit3"))).count(),"drawsHit4",metrics.stream().filter(m->Boolean.TRUE.equals(m.get("hit4"))).count(),
                        "meanCoverage",metrics.stream().mapToDouble(m->((Number)m.get("coverageCount")).doubleValue()).average().orElse(0)));
            }
            categories.add(map("category",category,"eligibleDraws",rows.size(),"evaluatedDraws",measured.size(),"summary",summary,"byDraw",measured));
        }
        var report=map("protocol",WorkspaceProtocol.VERSION,"codeVersion",WorkspaceProtocol.codeVersion(),"warmupDraws",10,
                "inputSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)),"parameters",map("primary","temporal_learning","secondary","bayesian_graph","weights",List.of(.25,.5,.75),"hyperparameters","defaults"),
                "limitations","Development replay on previously inspected results. Not historical production configuration, not human choices, not prospective validation. Control budget equals each distinct portfolio. No parameter/seed selection from this report; defaults unchanged.","categories",categories);
        var output=Path.of(System.getProperty("workspace.report.output","docs/reports/workspace-development-2026-10-28.json"));
        Files.createDirectories(output.toAbsolutePath().getParent()); mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);
    }
}
