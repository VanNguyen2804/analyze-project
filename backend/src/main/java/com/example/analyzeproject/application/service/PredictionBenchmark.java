package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.domain.service.*;
import java.util.*;
import java.util.stream.IntStream;
import static com.example.analyzeproject.application.service.ForecastProtocol.map;

/** Offline development replay. Never captures forecasts, settles ledgers or promotes a model. */
public final class PredictionBenchmark {
    public static final String VERSION = "chronological-study-v2";
    public record Options(int warmupDraws, int maximumTargets, int trainingWindow, int budget) {
        public Options {
            if (warmupDraws < 10 || warmupDraws > 1000 || maximumTargets < 1 || maximumTargets > 60
                    || trainingWindow < 10 || trainingWindow > 250 || budget < 1 || budget > 5)
                throw new IllegalArgumentException("Invalid benchmark options");
        }
        public static Options defaults() { return new Options(10, 30, 180, 5); }
    }
    private final AnalyzeService analyze;
    public PredictionBenchmark(AnalyzeService analyze) { this.analyze = analyze; }

    public Map<String,Object> run(List<LotteryNumber> input, String category, Options options) {
        if (!Set.of("MEGA", "POWER").contains(category)) throw new IllegalArgumentException("Invalid category");
        var rows = DrawQuality.history(input).stream().filter(r -> category.equals(r.getCategory()))
                .sorted(Comparator.comparing(LotteryNumber::getDrawDate)).toList();
        var manifest = map("protocol", VERSION, "evidenceType", "DEVELOPMENT_REPLAY", "category", category,
                "eligibleDraws", rows.size(), "budget", options.budget(), "options", options,
                "historyHash", ForecastProtocol.hash(rows.stream().map(DrawQuality::fingerprint).toList()),
                "modelPolicyVersion", "logistic-ablation-v1", "generatorVersions", List.of(TicketPortfolio.VERSION, CoveragePortfolio.VERSION),
                "codeVersion",WorkspaceProtocol.fingerprint(PredictionBenchmark.class,AiLabModel.class,AiLabModel.TrainingPolicy.class,
                        CoveragePortfolio.class,CoveragePortfolio.Policy.class,AnalyzeService.class,PredictionSupport.class,
                        TemporalRelationshipModel.class,PairRelationshipModel.class,DrawTransitionModel.class),
                "featureSchema",List.of("intercept","frequency","recent10","decay10","gap30"),
                "seedProtocol", VERSION + ":category:targetDate", "primaryMetric", "draw_hit3_percent",
                "uniformReplicates",20,"replicateSamplingUnit","One draw; each replicate has the same ticket budget and is not another independent draw.",
                "limitations", "Previously inspectable development data; chronological replay is not prospective evidence. No automatic promotion. Draws, not tickets or random replicates, are the sampling unit.");
        int start = Math.max(options.warmupDraws() + Math.max(3, (rows.size() - options.warmupDraws()) / 2), rows.size() - options.maximumTargets());
        if (start >= rows.size()) return map("manifest", manifest, "status", "INSUFFICIENT_DATA", "byDraw", List.of(), "summary", List.of());
        int innerStart = Math.max(options.warmupDraws(), start - 6);
        var candidates = List.of(
                new AiLabModel.TrainingPolicy(80, .2, .01, 15, 0),
                AiLabModel.TrainingPolicy.defaults(),
                new AiLabModel.TrainingPolicy(80, .2, .2, 15, 0),
                new AiLabModel.TrainingPolicy(80, .2, .05, 7, 0),
                new AiLabModel.TrainingPolicy(80, .2, .05, 1, 0));
        var tuning = new ArrayList<Map<String,Object>>();
        AiLabModel.TrainingPolicy selected = candidates.get(0); double best = Double.POSITIVE_INFINITY;
        var calibrationPairs = new ArrayList<double[]>();
        for (var candidate : candidates) {
            double loss = 0; int count = 0; var pairs = new ArrayList<double[]>();
            for (int i = innerStart; i < start; i++) {
                if (!hasPrevious(rows, i, category)) continue;
                var p = AiLabModel.fit(prefix(rows, i, options), category, "logistic", candidate).scores();
                loss += brier(p, rows.get(i).getNumbers()); count++;
                for (int n = 1; n < p.length; n++) pairs.add(new double[]{p[n], rows.get(i).getNumbers().contains(n) ? 1 : 0});
            }
            double mean = count == 0 ? Double.POSITIVE_INFINITY : loss / count;
            tuning.add(map("policy", candidate, "validationDraws", count, "brier", count == 0 ? null : mean));
            if (mean < best) { best = mean; selected = candidate; calibrationPairs = pairs; }
        }
        double prior = 8, bestPrior = Double.POSITIVE_INFINITY;
        for (double k : List.of(8.0, 32.0)) {
            double loss = 0; int count = 0;
            for (int i = innerStart; i < start; i++) if (hasPrevious(rows, i, category)) {
                loss += brier(AiLabModel.shrunkFrequency(prefix(rows, i, options), category, k), rows.get(i).getNumbers()); count++;
            }
            tuning.add(map("priorDraws", k, "validationDraws", count, "brier", count == 0 ? null : loss / count));
            if (count > 0 && loss / count < bestPrior) { bestPrior = loss / count; prior = k; }
        }
        if (!Double.isFinite(best)) return map("manifest", manifest, "status", "INSUFFICIENT_VALIDATION", "byDraw", List.of(), "summary", List.of());
        double selectedLambda=0, bestShrinkLoss=Double.POSITIVE_INFINITY;
        for(double lambda:List.of(0.0,.25,.5,.75,1.0)) {
            double loss=0;int count=0;
            for(int i=innerStart;i<start;i++) if(hasPrevious(rows,i,category)) {
                var scores=AiLabModel.fit(prefix(rows,i,options),category,"logistic",selected).scores();
                loss+=brier(AiLabModel.shrinkMarginals(scores,lambda),rows.get(i).getNumbers());count++;
            }
            tuning.add(map("shrinkLambda",lambda,"validationDraws",count,"brier",count==0?null:loss/count));
            if(count>0 && loss/count<bestShrinkLoss){bestShrinkLoss=loss/count;selectedLambda=lambda;}
        }
        manifest.put("shrinkLambda",selectedLambda);
        double[] calibrator = calibrate(calibrationPairs);
        manifest.put("selection", map("innerFrom", rows.get(innerStart).getDrawDate().toString(), "innerThrough", rows.get(start - 1).getDrawDate().toString(),
                "outerFrom", rows.get(start).getDrawDate().toString(), "outerThrough", rows.get(rows.size() - 1).getDrawDate().toString(),
                "selectedLogistic", selected, "priorDraws", prior, "calibrator", calibrator, "trials", tuning));
        var observations = new ArrayList<Map<String,Object>>();
        var feedback = new ArrayList<AiLabModel.FeedbackRound>();
        // Simulated feedback features are generated before each historical outcome and kept only in this replay.
        for (int i = options.warmupDraws(); i < start; i++) if (hasPrevious(rows, i, category)) {
            var fit = AiLabModel.fit(prefix(rows, i, options), category, "logistic");
            feedback.add(round(rows.get(i), fit, sample(fit.scores(), category, rows.get(i), options.budget())));
        }
        var excluded = new ArrayList<Map<String,Object>>();
        for (int i = start; i < rows.size(); i++) {
            if (!hasPrevious(rows, i, category)) { excluded.add(map("date", rows.get(i).getDrawDate().toString(), "reason", "MISSING_PREVIOUS_DRAW")); continue; }
            var target = rows.get(i); var history = prefix(rows, i, options); int max = "POWER".equals(category) ? 55 : 45;
            var probabilities = new LinkedHashMap<String,double[]>();
            double[] uniform = new double[max + 1]; Arrays.fill(uniform, 6.0 / max); uniform[0] = 0;
            probabilities.put("uniform", uniform);
            probabilities.put("frequency", AiLabModel.shrunkFrequency(history, category, 1e-9));
            probabilities.put("shrunk_frequency", AiLabModel.shrunkFrequency(history, category, prior));
            var fit = AiLabModel.fit(history, category, "logistic");
            probabilities.put("logistic_default", fit.scores());
            // A bounded training window also bounds feedback to results actually in that history.
            var dates = history.stream().map(r -> r.getDrawDate().toString()).collect(java.util.stream.Collectors.toSet());
            var permittedFeedback = feedback.stream().filter(r -> dates.contains(r.targetDate())).toList();
            probabilities.put("logistic_feedback_simulated", AiLabModel.fit(history, category, "logistic", permittedFeedback, AiLabModel.FeedbackPolicy.defaults()).scores());
            var selectedFit=AiLabModel.fit(history, category, "logistic", selected);
            double[] tuned = selectedFit.scores();
            probabilities.put("logistic_selected", tuned);
            probabilities.put("logistic_shrunk", AiLabModel.shrinkMarginals(tuned,selectedLambda));
            double[] calibrated = tuned.clone();
            for (int n = 1; n <= max; n++) calibrated[n] = sigmoid(calibrator[0] + calibrator[1] * logit(tuned[n]));
            probabilities.put("logistic_calibrated", calibrated);
            var methods = new LinkedHashMap<String,Object>();
            for (var model : probabilities.entrySet()) {
                var tickets = sample(model.getValue(), category, target, options.budget());
                methods.put(model.getKey(), outcome(tickets, target, model.getValue()));
            }
            List<Integer> ranking = ranking(tuned);
            methods.put("logistic_rank_portfolio", outcome(TicketPortfolio.select(ranking, max, options.budget()), target, null));
            methods.put("logistic_coverage", outcome(CoveragePortfolio.select(ranking, max, options.budget(), VERSION + category + target.getDrawDate(), CoveragePortfolio.Policy.defaults()), target, null));
            methods.put("logistic_triple_coverage", outcome(CoveragePortfolio.selectTriples(ranking,max,options.budget(),VERSION+category+target.getDrawDate(),CoveragePortfolio.Policy.defaults()),target,null));
            if (analyze != null) {
                var primary = analyze.analyzeAndPredictWithConfiguration(category, "temporal_learning", "bayesian_graph", history, null);
                var stack = analyze.analyzeAndPredictWithConfiguration(category, "deep_stacking", "bayesian_graph", history, null);
                for (var result : List.of(primary.getPrimaryAlgorithmResult(), primary.getSecondaryAlgorithmResult(), stack.getPrimaryAlgorithmResult())) {
                    @SuppressWarnings("unchecked") var ranked = (List<Integer>)result.get("ranking");
                    if (ranked == null || ranked.size() != max) throw new IllegalStateException("Scorer must expose its complete ranking");
                    // Same seeded weighted generator for every ranking-only model; no probability claims for rank scores.
                    double[] utilities = new double[max + 1]; for (int rank = 0; rank < max; rank++) utilities[ranked.get(rank)] = (max - rank) / (double) max;
                    var measured=outcome(sample(utilities, category, target, options.budget()), target, null);measured.put("ranking",ranked);
                    methods.put((String) result.get("id"), measured);
                }
            }
            observations.add(map("targetDate", target.getDrawDate().toString(), "trainingThrough", history.get(history.size() - 1).getDrawDate().toString(),
                    "trainingDraws", history.size(), "historyHash", ForecastProtocol.hash(history.stream().map(DrawQuality::fingerprint).toList()),
                    "actualNumbers", target.getNumbers(), "feedbackDraws", permittedFeedback.size(), "methods", methods,"selectedWeights",selectedFit.weights(),
                    "uniformReference",uniformReference(uniform,target,category,options.budget())));
            feedback.add(round(target, fit, sample(fit.scores(), category, target, options.budget())));
        }
        var summary = summarize(observations, options.budget());
        manifest.put("excluded", excluded); manifest.put("comparisonStatus", "DESCRIPTIVE_ONLY");
        manifest.put("confirmation", map("status", "NOT_REGISTERED", "automaticPromotion", false,
                "reason", "Register effect size, sample size, comparison family and review date before prospective confirmation."));
        return map("manifest", manifest, "status", observations.isEmpty() ? "INSUFFICIENT_DATA" : "COMPLETED", "byDraw", observations, "summary", summary);
    }

    private static List<LotteryNumber> prefix(List<LotteryNumber> rows, int target, Options options) {
        return rows.subList(Math.max(0, target - options.trainingWindow()), target);
    }
    private static boolean hasPrevious(List<LotteryNumber> rows, int index, String category) {
        return index > 0 && rows.get(index - 1).getDrawDate().equals(ForecastProtocol.previous(category, rows.get(index).getDrawDate()));
    }
    private static List<Integer> ranking(double[] scores) {
        return IntStream.range(1, scores.length).boxed().sorted(Comparator.<Integer>comparingDouble(n -> scores[n]).reversed().thenComparingInt(n -> n)).toList();
    }
    private static List<List<Integer>> sample(double[] scores, String category, LotteryNumber target, int budget) {
        return AiLabModel.tickets(scores, VERSION + ":" + category + ":" + target.getDrawDate()).subList(0, budget);
    }
    private static Map<String,Object> uniformReference(double[] scores,LotteryNumber target,String category,int budget) {
        double average=0;int hit3=0,hit4=0;
        for(int replicate=0;replicate<20;replicate++) {
            var tickets=AiLabModel.tickets(scores,VERSION+":"+category+":"+target.getDrawDate()+":replicate:"+replicate).subList(0,budget);
            int total=0,best=0;
            for(var ticket:tickets) {int matches=(int)ticket.stream().filter(target.getNumbers()::contains).count();total+=matches;best=Math.max(best,matches);}
            average+=total/(double)budget;if(best>=3)hit3++;if(best>=4)hit4++;
        }
        return map("replicates",20,"budget",budget,"averageMatched",average/20,"drawHit3Percent",100.0*hit3/20,"drawHit4Percent",100.0*hit4/20);
    }
    private static AiLabModel.FeedbackRound round(LotteryNumber target, AiLabModel.Fit fit, List<List<Integer>> tickets) {
        Set<Integer> chosen = new HashSet<>(); tickets.forEach(chosen::addAll);
        var examples = IntStream.range(1, fit.scores().length).mapToObj(n -> new AiLabModel.FeedbackExample(n, fit.features()[n], chosen.contains(n), target.getNumbers().contains(n))).toList();
        return new AiLabModel.FeedbackRound(target.getDrawDate().toString(), examples);
    }
    private static double[] calibrate(List<double[]> pairs) {
        double a = 0, b = 1;
        for (int epoch = 0; epoch < 100 && !pairs.isEmpty(); epoch++) {
            double da = 0, db = 0;
            for (double[] pair : pairs) { double x = logit(pair[0]); double e = sigmoid(a + b * x) - pair[1]; da += e; db += e * x; }
            a -= .05 * da / pairs.size(); b -= .05 * (db / pairs.size() + .01 * (b - 1));
        }
        return new double[]{a, b};
    }
    private static double sigmoid(double x) { return 1 / (1 + Math.exp(-Math.max(-30, Math.min(30, x)))); }
    private static double logit(double p) { p = Math.max(1e-12, Math.min(1 - 1e-12, p)); return Math.log(p / (1 - p)); }
    private static double brier(double[] scores, List<Integer> actual) {
        double value = 0; for (int n = 1; n < scores.length; n++) value += Math.pow(scores[n] - (actual.contains(n) ? 1 : 0), 2);
        return value / (scores.length - 1);
    }
    private static Map<String,Object> outcome(List<List<Integer>> tickets, LotteryNumber target, double[] scores) {
        var matches = tickets.stream().map(t -> (int)t.stream().filter(target.getNumbers()::contains).count()).toList();
        double loss = 0; if (scores != null) for (int n = 1; n < scores.length; n++) {
            double p = Math.max(1e-15, Math.min(1 - 1e-15, scores[n])); loss -= target.getNumbers().contains(n) ? Math.log(p) : Math.log1p(-p);
        }
        return map("tickets", tickets, "matches", matches, "averageMatched", matches.stream().mapToInt(n -> n).average().orElseThrow(),
                "bestMatched", Collections.max(matches), "topTicketMatched", matches.get(0),
                "brier", scores == null ? null : brier(scores, target.getNumbers()), "logLoss", scores == null ? null : loss / (scores.length - 1),
                "marginalProbabilities",scores==null?null:Arrays.copyOfRange(scores,1,scores.length),
                "ranking",scores==null?null:ranking(scores),"scoreType",scores==null?"ranking_only":"marginal_probability",
                "diagnostics", CoveragePortfolio.diagnostics(tickets));
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> summarize(List<Map<String,Object>> observations, int budget) {
        if (observations.isEmpty()) return List.of();
        var methods = (Map<String,Object>) observations.get(0).get("methods"); var result = new ArrayList<Map<String,Object>>();
        for (String method : methods.keySet()) {
            int[] histogram = new int[7], draws = new int[7]; double sum = 0, brier = 0, logLoss = 0; int probabilityDraws = 0;
            var deltas = new ArrayList<Double>();
            for (var row : observations) {
                var all = (Map<String,Map<String,Object>>)row.get("methods"); var outcome = all.get(method);
                for (int matched : (List<Integer>)outcome.get("matches")) { histogram[matched]++; sum += matched; }
                int best = (Integer)outcome.get("bestMatched"); for (int k = 1; k <= best; k++) draws[k]++;
                if (outcome.get("brier") instanceof Number n) { brier += n.doubleValue(); logLoss += ((Number)outcome.get("logLoss")).doubleValue(); probabilityDraws++; }
                deltas.add(((Number)outcome.get("averageMatched")).doubleValue() - ((Number)all.get("uniform").get("averageMatched")).doubleValue());
            }
            double mean = deltas.stream().mapToDouble(n -> n).average().orElseThrow();
            double variance = deltas.stream().mapToDouble(n -> Math.pow(n - mean, 2)).sum() / Math.max(1, deltas.size() - 1);
            double margin = 1.96 * Math.sqrt(variance / deltas.size());
            result.add(map("model", method, "evaluatedDraws", observations.size(), "evaluatedTickets", observations.size() * budget,
                    "budget", budget, "averageMatched", sum / (observations.size() * budget), "matchHistogram", histogram,
                    "drawHit3Percent", 100.0 * draws[3] / observations.size(), "drawHit4Percent", 100.0 * draws[4] / observations.size(),
                    "drawHit5Percent", 100.0 * draws[5] / observations.size(), "drawHit4Count", draws[4],
                    "drawHit4Interval95", wilson(draws[4], observations.size()), "brier", probabilityDraws == 0 ? null : brier / probabilityDraws,
                    "logLoss", probabilityDraws == 0 ? null : logLoss / probabilityDraws,
                    "pairedDifferenceVsUniform", mean, "descriptiveInterval95", deltas.size() < 2 ? null : List.of(mean - margin, mean + margin),
                    "evidenceStatus", "DESCRIPTIVE_ONLY_MULTIPLE_COMPARISONS"));
        }
        return result;
    }
    public static List<Double> wilson(int events, int total) {
        if (total <= 0) return List.of();
        double z = 1.959963984540054, p = events / (double)total, denominator = 1 + z * z / total;
        double center = (p + z * z / (2 * total)) / denominator;
        double half = z * Math.sqrt(p * (1 - p) / total + z * z / (4.0 * total * total)) / denominator;
        return List.of(100 * Math.max(0, center - half), 100 * Math.min(1, center + half));
    }
}
