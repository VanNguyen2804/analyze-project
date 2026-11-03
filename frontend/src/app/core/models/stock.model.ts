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
export interface StockPositionInput {
  purchaseDate: string;
  purchasePrice: number | null;
  quantity: number | null;
  buyFeePercent: number | null;
  sellCostPercent: number | null;
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
