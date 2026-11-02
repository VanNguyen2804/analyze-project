package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.service.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Explicit changes accept only parameters that the current scorer actually reads. */
public final class PredictionParameters {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Map<String,double[]> RANGES=Map.ofEntries(
            Map.entry("momentumDecayRate",new double[]{0,10}), Map.entry("coOccurrenceWeight",new double[]{0,100}),
            Map.entry("pairGraphPriorStrength",new double[]{0.01,10000}), Map.entry("pairGraphMinSupport",new double[]{1,1000}),
            Map.entry("extremeGanReboundBonus",new double[]{0,100}),
            Map.entry("specialToMainMigrationWeight",new double[]{0,100}),Map.entry("adaptiveRepeatWeight",new double[]{0,100}),
            Map.entry("temporalPriorStrength",new double[]{0.01,10000}),Map.entry("temporalLearningRate",new double[]{0,10}),
            Map.entry("temporalMinHistory",new double[]{1,1000}));
    private PredictionParameters() {}
    public static String merge(String original,String patch) {
        try {
            JsonNode base=JSON.readTree(original==null || original.isBlank()?"{}":original), changes=JSON.readTree(patch);
            if(!(base instanceof ObjectNode result) || !(changes instanceof ObjectNode) || changes.isEmpty()) throw new IllegalArgumentException("Cần một object chứa tham số thay đổi.");
            var fields=changes.fields();
            while(fields.hasNext()) {
                var field=fields.next(); var range=RANGES.get(field.getKey()); var value=field.getValue();
                if(range==null) throw new IllegalArgumentException("Tham số không được thuật toán hiện tại sử dụng: "+field.getKey());
                if(!value.isNumber() || !Double.isFinite(value.doubleValue()) || value.doubleValue()<range[0] || value.doubleValue()>range[1]
                        || Set.of("temporalMinHistory","pairGraphMinSupport").contains(field.getKey()) && value.doubleValue()!=value.intValue())
                    throw new IllegalArgumentException("Giá trị tham số không hợp lệ: "+field.getKey());
                // The scorer gives recommendedAdjustments precedence; preserve that existing structure.
                if(result.has("recommendedAdjustments")) {
                    if(!(result.get("recommendedAdjustments") instanceof ObjectNode nested)) throw new IllegalArgumentException("Cấu hình hiện tại sai định dạng.");
                    nested.set(field.getKey(),value);
                } else result.set(field.getKey(),value);
            }
            return JSON.writeValueAsString(result);
        } catch(IllegalArgumentException e) { throw e; } catch(Exception e) { throw new IllegalArgumentException("JSON tham số không hợp lệ."); }
    }
}
