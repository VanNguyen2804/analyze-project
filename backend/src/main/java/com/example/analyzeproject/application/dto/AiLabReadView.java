package com.example.analyzeproject.application.dto;

import com.example.analyzeproject.application.service.ForecastProtocol;
import java.util.*;

/** Internal read projection. Validation happens against the original immutable snapshot before trimming history. */
public record AiLabReadView(List<Snapshot> forecasts, List<Map<String,Object>> settlements) {
    public AiLabReadView { forecasts=List.copyOf(forecasts);settlements=List.copyOf(settlements); }
    public record Snapshot(Map<String,Object> data, boolean prospective) {}
    public static Snapshot project(Map<String,Object> original) {
        boolean valid=ForecastProtocol.isProspective(original);
        Map<String,Object> small=new LinkedHashMap<>();
        for(String key:List.of("id","category","algorithm","labModel","slot","targetDate","createdAt","numbers",
                "frequencyNumbers","randomNumbers","modelVersion","codeVersion","configuration","configurationHash",
                "historyHash","trainingThrough","experimentId","modelPolicyVersion","evaluationVersion"))
            if(original.containsKey(key))small.put(key,original.get(key));
        if(original.get("explanation") instanceof Map<?,?> explanation) {
            Map<String,Object> projected=new LinkedHashMap<>();
            if(original.get("slot") instanceof Number slot && slot.intValue()==0 && explanation.get("details") instanceof List<?> details) projected.put("details",details.stream().map(item->{
                if(!(item instanceof Map<?,?> detail))return item;
                Map<String,Object> value=new LinkedHashMap<>();value.put("number",detail.get("number"));value.put("score",detail.get("score"));return value;
            }).toList());
            if(explanation.get("feedback") instanceof Map<?,?> feedback) projected.put("feedback",ForecastProtocol.map(
                    "draws",feedback.get("draws"),"tickets",feedback.get("tickets"),"ticketMatchHistogram",feedback.get("ticketMatchHistogram")));
            small.put("explanation",projected);
        }
        return new Snapshot(Collections.unmodifiableMap(small),valid);
    }
    @SuppressWarnings("unchecked")
    public static AiLabReadView fromLedger(Map<String,Object> ledger,String category) {
        var forecasts=((List<Map<String,Object>>)ledger.getOrDefault("forecasts",List.of())).stream()
                .filter(f->category.equals(f.get("category"))).map(AiLabReadView::project).toList();
        Map<Object,Map<String,Object>> latest=new LinkedHashMap<>();
        ((List<Map<String,Object>>)ledger.getOrDefault("settlements",List.of())).forEach(e->latest.put(e.get("forecastId"),e));
        return new AiLabReadView(forecasts,new ArrayList<>(latest.values()));
    }
}
