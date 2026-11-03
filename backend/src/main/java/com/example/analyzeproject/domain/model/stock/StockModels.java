package com.example.analyzeproject.domain.model.stock;

import java.time.*;
import java.util.List;

public final class StockModels {
    private StockModels() {}
    public enum Mode { INTRADAY, SWING }
    public record Candle(long time, double open, double high, double low, double close, double volume, boolean closed) {
        public Candle {
            if (time <= 0 || !Double.isFinite(open) || !Double.isFinite(high) || !Double.isFinite(low)
                    || !Double.isFinite(close) || !Double.isFinite(volume) || low <= 0 || volume < 0
                    || low > Math.min(open, close) || high < Math.max(open, close))
                throw new IllegalArgumentException("Nến không hợp lệ");
        }
    }
    public record Feed(List<Candle> candles, Instant fetchedAt, String source, String unit, String priceBasis,
                       boolean verified, List<String> warnings) {
        public Feed { candles = List.copyOf(candles); warnings = List.copyOf(warnings); }
    }
    public record Indicator(double fastMa, double slowMa, double rsi, double atr, double support, double resistance) {}
    public record FeatureWeight(String feature, double buyWeight, double sellWeight) {}
    public record ValidationFold(long trainingLabelThrough, long validationFrom, long validationThrough) {}
    public record Learning(String version, String status, String signal, int horizonBars, int trainSamples,
                           int testSamples, Double accuracy, Double balancedAccuracy, Double baselineAccuracy,
                           Double logLoss, Double buyScore, Double holdScore, Double sellScore,
                           Long trainingLabelsThrough, List<FeatureWeight> weights, List<ValidationFold> validationFolds, String explanation) {}
    public record Recommendation(String version, String action, String title, String summary, String unit,
                                 Long asOf, Double evaluatedPrice, Double buyAbove, Double sellBelow,
                                 Double stopLoss, Double takeProfit, Double volumeRatio, double volumeConfirmation,
                                 List<String> buyConditions, List<String> sellConditions, List<String> cautions) {
        public Recommendation { buyConditions=List.copyOf(buyConditions);sellConditions=List.copyOf(sellConditions);cautions=List.copyOf(cautions); }
    }
    public record Analysis(String signal, List<String> reasons, Indicator indicators, Learning learning, Recommendation recommendation) {}
    public record Snapshot(String symbol, Mode mode, String interval, String snapshotId, Feed data, Feed benchmark,
                           String freshness, String marketStatus, Long lastClosedBarAt, Analysis analysis, DailyDesk dailyDesk) {}
    public record Reading(String key, String name, String current, String buyTarget, String sellTarget,
                          boolean buyReady, boolean sellReady, String explanation) {}
    public record FibonacciLevel(double ratio, double price) {}
    public record DailyValues(Double rsi, Double previousRsi, Double ema20, Double ema50, Double ema200,
                              Double tenkan, Double kijun, Double cloudA, Double cloudB,
                              Double volumeAverage, Double volumeRatio, Double rangeLow, Double rangeHigh,
                              String rangeLowDate, String rangeHighDate, String swingDirection, List<FibonacciLevel> fibonacci) {}
    public record DailyAssessment(String date, long asOf, double price, Double changePercent, String bias,
                                  String summary, int buyConfirmations, int sellConfirmations, DailyValues values,
                                  List<Reading> readings, List<String> cautions) {}
    public record DailyDesk(String version, String unit, String priceBasis, List<DailyAssessment> days) {}
    public record PositionInput(LocalDate purchaseDate, Double purchasePrice, Double quantity,
                                Double buyFeePercent, Double sellCostPercent, Double maxLossPercent,
                                Double sellableQuantity) {
        @Override public String toString(){return "PositionInput[redacted]";}
    }
    public record PositionResult(String snapshotId, String status, Double price, Double changePercent,
                                 Double grossPnl, Double estimatedNetPnl, Double breakEven, List<String> reasons) {
        @Override public String toString(){return "PositionResult[redacted]";}
    }
}
