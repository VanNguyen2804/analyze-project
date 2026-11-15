package com.example.analyzeproject.domain.service;
import java.util.*;
/** Outcome diagnostics on one complete, equal-budget batch. */
public final class PortfolioDiagnostics {
    private PortfolioDiagnostics() {}
    public static Map<String,Object> measure(List<List<Integer>> tickets, List<Integer> actual, List<Integer> ranking) {
        var selected=new HashSet<Integer>(); tickets.forEach(selected::addAll);
        var winners=new HashSet<>(actual);
        int union=(int)selected.stream().filter(winners::contains).count();
        int best=tickets.stream().mapToInt(t->(int)t.stream().filter(winners::contains).count()).max().orElse(0);
        Map<String,Object> result=new LinkedHashMap<>(CoveragePortfolio.diagnostics(tickets));
        result.put("unionHits",union);result.put("bestTicketHits",best);result.put("assemblyGap",union-best);
        result.put("uniqueNumberCount",selected.size());
        result.put("conditionalHit4",union>=4?best>=4:null);result.put("conditionalHit5",union>=5?best>=5:null);
        Map<String,Integer> top=new LinkedHashMap<>();
        if(ranking!=null&&!ranking.isEmpty())for(int k:List.of(6,10,12,16))
            top.put(Integer.toString(k),(int)ranking.stream().limit(k).filter(winners::contains).count());
        Map<String,Double> recall=new LinkedHashMap<>();top.forEach((k,v)->recall.put(k,v/6.0));
        result.put("topKHits",top);result.put("topKRecall",recall);return result;
    }
}
