export type StockMode = 'INTRADAY' | 'SWING';
export interface StockWatchQuote {
  symbol: string;
  price: number | null;
  asOf: number | null;
  unit: string;
  freshness: string;
}
export interface StockCandle {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  closed: boolean;
}
export interface StockFeed {
  candles: StockCandle[];
  fetchedAt: string;
  source: string;
  unit: string;
  priceBasis: string;
  verified: boolean;
  warnings: string[];
}
export interface StockLearning {
  version: string;
  status: string;
  signal: string;
  horizonBars: number;
  trainSamples: number;
  testSamples: number;
  accuracy: number | null;
  balancedAccuracy: number | null;
  baselineAccuracy: number | null;
  logLoss: number | null;
  buyScore: number | null;
  holdScore: number | null;
  sellScore: number | null;
  trainingLabelsThrough: number | null;
  weights: { feature: string; buyWeight: number; sellWeight: number }[];
  explanation: string;
}
export interface StockSnapshot {
  dailyDesk?: StockDailyDesk;
  symbol: string;
  mode: StockMode;
  interval: string;
  snapshotId: string;
  data: StockFeed;
  benchmark: StockFeed;
  freshness: string;
  marketStatus: string;
  lastClosedBarAt: number | null;
  analysis: {
    recommendation?: StockRecommendation;
    signal: string;
    reasons: string[];
    indicators: {
      fastMa: number;
      slowMa: number;
      rsi: number;
      atr: number;
      support: number;
      resistance: number;
    } | null;
    learning: StockLearning;
  };
}
export interface StockReading {
  key: string;
  name: string;
  current: string;
  buyTarget: string;
  sellTarget: string;
  buyReady: boolean;
  sellReady: boolean;
  explanation: string;
}
export interface StockDailyAssessment {
  date: string;
  asOf: number;
  price: number;
  changePercent: number | null;
  bias: string;
  summary: string;
  buyConfirmations: number;
  sellConfirmations: number;
  readings: StockReading[];
  cautions: string[];
  values: {
    rsi: number | null;
    previousRsi: number | null;
    ema20: number | null;
    ema50: number | null;
    ema200: number | null;
    tenkan: number | null;
    kijun: number | null;
    cloudA: number | null;
    cloudB: number | null;
    volumeAverage: number | null;
    volumeRatio: number | null;
    rangeLow: number | null;
    rangeHigh: number | null;
    rangeLowDate: string | null;
    rangeHighDate: string | null;
    swingDirection: string;
    fibonacci: { ratio: number; price: number }[];
  };
}
export interface StockDailyDesk {
  version: string;
  unit: string;
  priceBasis: string;
  days: StockDailyAssessment[];
}
export interface StockRecommendation {
  version: string;
  action: string;
  title: string;
  summary: string;
  unit: string;
  asOf: number | null;
  evaluatedPrice: number | null;
  buyAbove: number | null;
  sellBelow: number | null;
  stopLoss: number | null;
  takeProfit: number | null;
  volumeRatio: number | null;
  volumeConfirmation: number;
  buyConditions: string[];
  sellConditions: string[];
  cautions: string[];
}
export interface StockPositionInput {
  purchaseDate: string;
  purchasePrice: number | null;
  quantity: number | null;
  maxLossPercent: number | null;
  sellableQuantity: number | null;
}
export interface StockPositionResult {
  snapshotId: string;
  status: string;
  price: number | null;
  changePercent: number | null;
  grossPnl: number | null;
  estimatedNetPnl: number | null;
  breakEven: number | null;
  reasons: string[];
}
