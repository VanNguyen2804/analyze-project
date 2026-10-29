package com.example.analyzeproject.service;

import java.util.*;
import java.util.stream.IntStream;
import static com.example.analyzeproject.service.ForecastProtocol.map;

/** Only freezes values already produced by scoring; it never scores or chooses a ticket. */
public final class PredictionExplanation {
    public static final String VERSION="frozen-ranking-v1";
    private PredictionExplanation() {}
    public static Map<String,Object> fromResult(Map<String,Object> result) {
        return map("schemaVersion",VERSION,"scoreType","internal_score","ranking",result.get("ranking"),
                "details",result.getOrDefault("allDetails",List.of()),"tieBreak","number_ascending",
                "modelWeights",result.getOrDefault("modelWeights",Map.of()),"scoringPolicy",result.getOrDefault("scoringPolicy","heuristic"));
    }
    public static Map<String,Object> lab(AiLabModel.Fit fit,String model) {
        double[] scores=fit.scores();
        var ranking=IntStream.range(1,scores.length).boxed().sorted(Comparator.<Integer>comparingDouble(n->scores[n]).reversed().thenComparingInt(n->n)).toList();
        String[] names={"intercept","frequency","recent10","decay10","gap30"};Map<String,Double> weights=new LinkedHashMap<>();
        if("logistic".equals(model)) for(int i=0;i<names.length;i++) weights.put(names[i],fit.weights()[i]);
        return map("schemaVersion",VERSION,"scoreType","uncalibrated_model_score","ranking",ranking,
                "details",ranking.stream().map(n->{ Map<String,Double> values=new LinkedHashMap<>();
                    for(int i=0;i<names.length;i++) if("logistic".equals(model)||i==3) values.put(names[i],fit.features()[n][i]);
                    return map("number",n,"score",scores[n],"features",values); }).toList(),"tieBreak","number_ascending","modelWeights",weights,"scoringPolicy",model);
    }
    public static Map<String,Object> combined(List<Integer> first,List<Integer> second,double weight) {
        Map<Integer,Double> scores=new HashMap<>();
        for(int i=0;i<first.size();i++) scores.merge(first.get(i),weight/(i+1),Double::sum);
        for(int i=0;i<second.size();i++) scores.merge(second.get(i),(1-weight)/(i+1),Double::sum);
        var ranking=scores.keySet().stream().sorted(Comparator.<Integer>comparingDouble(scores::get).reversed().thenComparingInt(n->n)).toList();
        return map("schemaVersion",VERSION,"scoreType","reciprocal_rank_score","ranking",ranking,
                "details",ranking.stream().map(n->map("number",n,"score",scores.get(n))).toList(),
                "primaryRanking",first,"secondaryRanking",second,"primaryWeight",weight,"tieBreak","number_ascending");
    }
}
