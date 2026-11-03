package com.example.analyzeproject.domain.service;

import com.example.analyzeproject.domain.model.LotteryNumber;
import java.util.*;
import java.util.stream.IntStream;

/** Small, deterministic online logistic model; every training feature precedes its label. */
public final class AiLabModel {
    public static final String VERSION = "ai-lab-dense-feedback-v2";
    public static final int BUDGET = 5;
    public static final List<String> MODELS = List.of("logistic", "recency");
    private AiLabModel() {}
    public record Fit(double[] scores, double[][] features, double[] weights) {}
    public record FeedbackPolicy(double learningRate, double selectedExtraWeight, int maximumDraws) {
        public FeedbackPolicy {
            if (!Double.isFinite(learningRate) || learningRate <= 0 || learningRate > .2
                    || !Double.isFinite(selectedExtraWeight) || selectedExtraWeight < 0 || selectedExtraWeight > 1
                    || maximumDraws < 1 || maximumDraws > 120)
                throw new IllegalArgumentException("Invalid feedback policy");
        }
        public static FeedbackPolicy defaults() { return new FeedbackPolicy(.05, .25, 60); }
    }
    public record FeedbackExample(int number, double[] features, boolean selected, boolean occurred) {
        public FeedbackExample { features = features.clone(); }
        @Override public double[] features() { return features.clone(); }
    }
    public record FeedbackRound(String targetDate, List<FeedbackExample> examples) {
        public FeedbackRound { examples = List.copyOf(examples); }
    }
    /** Replay dense, signed feedback once per settled draw; a 1/2-hit ticket is never discarded. */
    public static Fit fit(List<LotteryNumber> input, String category, String model,
                          List<FeedbackRound> feedback, FeedbackPolicy policy) {
        Fit base = fit(input, category, model);
        if (!"logistic".equals(model) || feedback.isEmpty()) return base;
        var actual = new HashMap<String,Set<Integer>>();
        DrawQuality.history(input).stream().filter(r -> category.equals(r.getCategory()))
                .forEach(r -> actual.put(r.getDrawDate().toString(), new HashSet<>(r.getNumbers())));
        var rounds = feedback.stream().sorted(Comparator.comparing(FeedbackRound::targetDate)).toList();
        Set<String> dates = new HashSet<>();
        double[] weights = base.weights().clone();
        for (var round : rounds.subList(Math.max(0, rounds.size() - policy.maximumDraws()), rounds.size())) {
            if (!dates.add(round.targetDate()) || !actual.containsKey(round.targetDate()))
                throw new IllegalArgumentException("Feedback must have one known historical result per date");
            if (round.examples().size() != base.scores().length - 1)
                throw new IllegalArgumentException("Feedback must cover every main number");
            Set<Integer> numbers = new HashSet<>();
            double[] gradient = new double[weights.length]; double totalWeight = 0;
            for (var example : round.examples()) {
                double[] x = example.features();
                if (example.number() < 1 || example.number() >= base.scores().length || !numbers.add(example.number())
                        || x.length != weights.length || x[0] != 1 || Arrays.stream(x).anyMatch(v -> !Double.isFinite(v) || v < 0 || v > 1)
                        || example.occurred() != actual.get(round.targetDate()).contains(example.number()))
                    throw new IllegalArgumentException("Invalid or unverified feedback example");
                // All positives and negatives remain in the loss; selected numbers receive a bounded extra weight.
                double weight = 1 + (example.selected() ? policy.selectedExtraWeight() : 0);
                double error = sigmoid(dot(weights, x)) - (example.occurred() ? 1 : 0);
                totalWeight += weight;
                for (int j = 0; j < weights.length; j++) gradient[j] += weight * error * x[j];
            }
            for (int j = 0; j < weights.length; j++) weights[j] -= policy.learningRate() * gradient[j] / totalWeight;
        }
        double[] scores = new double[base.scores().length];
        for (int n = 1; n < scores.length; n++) scores[n] = sigmoid(dot(weights, base.features()[n]));
        return new Fit(scores, base.features(), weights);
    }
    public static double[] scores(List<LotteryNumber> input, String category, String model) {
        return fit(input,category,model).scores();
    }
    public static Fit fit(List<LotteryNumber> input, String category, String model) {
        var rows = DrawQuality.history(input).stream().filter(r -> category.equals(r.getCategory()))
                .sorted(Comparator.comparing(LotteryNumber::getDrawDate)).toList();
        int max = "POWER".equals(category) ? 55 : 45;
        double[] weights = { Math.log(6.0 / (max - 6)), 0, 0, 0, 0 };
        List<double[]> features = new ArrayList<>(); List<Double> labels = new ArrayList<>();
        double[] counts=new double[max+1], recent=new double[max+1], decay=new double[max+1];
        int[] lastSeen=new int[max+1]; Arrays.fill(lastSeen,-1);
        double totalWeight=0, factor=Math.pow(0.5,0.1);
        double[][] current=new double[max+1][];
        // Compute each historical feature once, before consuming the corresponding outcome.
        for(int t=0;t<=rows.size();t++) {
            for(int n=1;n<=max;n++) {
                current[n]=new double[]{1,counts[n]/Math.max(1,t),recent[n]/Math.max(1,Math.min(10,t)),decay[n]/Math.max(1,totalWeight),Math.min(lastSeen[n]<0?t:t-1-lastSeen[n],30)/30.0};
                if("logistic".equals(model) && t>=5 && t<rows.size()) {
                    features.add(current[n]); labels.add(rows.get(t).getNumbers().contains(n)?1.0:0.0);
                }
            }
            if(t==rows.size()) break;
            for(int n=1;n<=max;n++) decay[n]*=factor;
            totalWeight=totalWeight*factor+1;
            if(t>=10) for(int n:rows.get(t-10).getNumbers()) recent[n]--;
            for(int n:rows.get(t).getNumbers()) { counts[n]++; recent[n]++; decay[n]++; lastSeen[n]=t; }
        }
        if ("logistic".equals(model)) {
            for (int epoch = 0; epoch < 80 && !features.isEmpty(); epoch++) {
                double[] gradient = new double[weights.length];
                for (int i = 0; i < features.size(); i++) {
                    double[] x = features.get(i); double error = sigmoid(dot(weights,x)) - labels.get(i);
                    for (int j = 0; j < weights.length; j++) gradient[j] += error*x[j];
                }
                for (int j = 0; j < weights.length; j++) weights[j] -= 0.2*(gradient[j]/features.size() + (j == 0 ? 0 : 0.05*weights[j]));
            }
        }
        double[] scores = new double[max+1];
        for (int n = 1; n <= max; n++) {
            double[] x = current[n];
            scores[n] = "logistic".equals(model) ? sigmoid(dot(weights,x)) : "frequency".equals(model) ? x[1]+0.01 : "random".equals(model) ? 1 : x[3]+0.01;
        }
        return new Fit(scores,current,weights);
    }
    private static double dot(double[] a,double[] b) { double v=0; for(int i=0;i<a.length;i++) v+=a[i]*b[i]; return v; }
    private static double sigmoid(double x) { return 1/(1+Math.exp(-Math.max(-30,Math.min(30,x)))); }
    public static List<List<Integer>> tickets(double[] scores, String seed) {
        Random random = new Random(seed.hashCode());
        boolean uniform = IntStream.range(1,scores.length).allMatch(n -> scores[n] == scores[1]);
        List<List<Integer>> result = new ArrayList<>(); Set<List<Integer>> seen = new HashSet<>();
        for(int attempt=0;result.size()<BUDGET && attempt<1000;attempt++) {
            Map<Integer,Double> keys = new HashMap<>();
            for(int n=1;n<scores.length;n++) keys.put(n, attempt==0 && !uniform ? scores[n] : Math.log(Math.max(1e-12,random.nextDouble()))/Math.max(1e-6,scores[n]));
            var ticket=IntStream.range(1,scores.length).boxed().sorted(Comparator.<Integer>comparingDouble(keys::get).reversed().thenComparingInt(n->n)).limit(6).sorted().toList();
            if(seen.add(ticket)) result.add(ticket);
        }
        if(result.size()!=BUDGET) throw new IllegalStateException("Cannot produce distinct tickets");
        return result;
    }
}
