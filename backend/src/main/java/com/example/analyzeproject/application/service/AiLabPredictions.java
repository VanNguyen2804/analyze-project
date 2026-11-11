package com.example.analyzeproject.application.service;

import java.util.*;
import static com.example.analyzeproject.application.service.ForecastProtocol.map;

/** Read-only ranking of upcoming Lab batches by observed, per-draw historical rates. */
final class AiLabPredictions {
    private AiLabPredictions() {}

    static List<Map<String,Object>> project(List<Map<String,Object>> rows,String target) {
        Map<List<Object>,List<Map<String,Object>>> batches=new LinkedHashMap<>();
        for(var row:rows) batches.computeIfAbsent(List.of(row.get("model"),row.get("targetDate")),k->new ArrayList<>()).add(row);
        Map<Object,List<List<Map<String,Object>>>> history=new HashMap<>();
        List<List<Map<String,Object>>> current=new ArrayList<>();
        for(var batch:batches.values()) {
            // Ambiguous duplicate batches, mixed cohorts and incomplete batches never enter either denominator.
            if(!AiLabService.completeBatch(batch)||batch.stream().map(r->r.get("cohort")).distinct().count()!=1)continue;
            String date=(String)batch.get(0).get("targetDate");
            if(date.equals(target)) {
                if(batch.stream().allMatch(r->r.get("settlement")==null||"PENDING".equals(event(r).get("status"))))current.add(batch);
            } else if(date.compareTo(target)<0&&settled(batch)) {
                history.computeIfAbsent(batch.get(0).get("model"),k->new ArrayList<>()).add(batch);
            }
        }
        List<Map<String,Object>> result=new ArrayList<>();
        for(var batch:current) {
            var first=batch.get(0);
            var past=history.getOrDefault(first.get("model"),List.of());
            Map<Object,List<List<Map<String,Object>>>> cohorts=new TreeMap<>();
            for(var draw:past)cohorts.computeIfAbsent(draw.get(0).get("cohort"),k->new ArrayList<>()).add(draw);
            var breakdown=cohorts.entrySet().stream().map(entry->{
                var stats=statistics(entry.getValue());
                stats.put("cohort",entry.getKey());
                stats.put("modelVersion",entry.getValue().get(0).get(0).get("modelVersion"));
                return stats;
            }).toList();
            result.add(map("model",first.get("model"),"modelVersion",first.get("modelVersion"),"cohort",first.get("cohort"),
                    "targetDate",target,"tickets",batch.stream().sorted(Comparator.comparingInt(r->((Number)r.get("slot")).intValue())).toList(),
                    "performance",statistics(past),"history",breakdown));
        }
        result.sort(Comparator.<Map<String,Object>>comparingDouble(r->rate(r,3)).reversed()
                .thenComparing(Comparator.comparingDouble((Map<String,Object> r)->rate(r,2)).reversed())
                .thenComparing(Comparator.comparingDouble((Map<String,Object> r)->rate(r,1)).reversed())
                .thenComparing(Comparator.comparingInt((Map<String,Object> r)->((Number)((Map<?,?>)r.get("performance")).get("evaluatedDraws")).intValue()).reversed())
                .thenComparing(r->(String)r.get("model")));
        return result;
    }

    private static Map<String,Object> statistics(List<List<Map<String,Object>>> draws) {
        var tickets=draws.stream().flatMap(List::stream).toList();
        List<Map<String,Object>> levels=new ArrayList<>();
        for(int threshold:List.of(3,2,1)) {
            long hits=draws.stream().filter(draw->draw.stream().anyMatch(r->matched(r)>=threshold)).count();
            levels.add(map("threshold",threshold,"drawHits",hits,"drawPercent",draws.isEmpty()?null:100.0*hits/draws.size()));
        }
        return map("evaluatedDraws",draws.size(),"evaluatedTickets",tickets.size(),"levels",levels,
                "hitTickets",tickets.stream().filter(r->matched(r)>=3).count(),
                "bestMatched",tickets.stream().mapToInt(AiLabPredictions::matched).max().orElse(0),
                "latestHitDate",tickets.stream().filter(r->matched(r)>=3).map(r->(String)r.get("targetDate")).max(String::compareTo).orElse(null));
    }

    private static boolean settled(List<Map<String,Object>> batch) {
        var first=event(batch.get(0));
        return first.get("actualNumbers") instanceof List<?> actual&&actual.size()==6&&batch.stream().allMatch(r->{
            var e=event(r);
            return "SETTLED".equals(e.get("status"))&&e.get("matched") instanceof Number n&&n.intValue()>=0&&n.intValue()<=6
                    &&Objects.equals(first.get("actualNumbers"),e.get("actualNumbers"))
                    &&Objects.equals(first.get("resultFingerprint"),e.get("resultFingerprint"));
        });
    }
    private static Map<?,?> event(Map<String,Object> row) { return row.get("settlement") instanceof Map<?,?> e?e:Map.of(); }
    private static int matched(Map<String,Object> row) { return ((Number)event(row).get("matched")).intValue(); }
    private static double rate(Map<String,Object> group,int threshold) {
        var performance=(Map<?,?>)group.get("performance");
        var levels=(List<?>)performance.get("levels");
        for(var entry:levels) {
            var level=(Map<?,?>)entry;
            if(Objects.equals(threshold,level.get("threshold")))return level.get("drawPercent") instanceof Number n?n.doubleValue():-1;
        }
        return -1;
    }
}
