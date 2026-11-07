import { LotteryCategory } from './lottery-category.model';

export interface EvidenceInventory {
  asOf: string;
  source: string;
  category: LotteryCategory;
  totalRows: number;
  eligibleDraws: number;
  ineligibleRows: number;
  fromDate: string | null;
  trainingThrough: string | null;
  expectedThrough: string;
  targetDate: string;
  fresh: boolean;
  missingDrawDates: string[];
  qualityCounts: Record<string, number>;
  historyHash: string;
  sourceNote: string;
  forecasts: {
    savedTickets: number;
    savedDraws: number;
    settledDraws: number;
    excludedTickets: number;
  };
  lab: { savedTickets: number; savedDraws: number; settledDraws: number; excludedTickets: number };
}
export interface BenchmarkSummary {
  model: string;
  evaluatedDraws: number;
  evaluatedTickets: number;
  budget: number;
  averageMatched: number;
  drawHit3Percent: number;
  drawHit4Percent: number;
  drawHit5Percent: number;
  drawHit4Count: number;
  drawHit4Interval95: number[];
  matchHistogram: number[];
  brier: number | null;
  logLoss: number | null;
  pairedDifferenceVsUniform: number;
  descriptiveInterval95: number[] | null;
}
export interface BenchmarkReport {
  status: string;
  manifest?: {
    protocol: string;
    evidenceType: string;
    category: LotteryCategory;
    budget: number;
    eligibleDraws: number;
    historyHash: string;
    limitations: string;
    selection?: {
      innerFrom: string;
      innerThrough: string;
      outerFrom: string;
      outerThrough: string;
    };
  };
  summary?: BenchmarkSummary[];
  byDraw?: { targetDate: string; trainingThrough: string; feedbackDraws: number }[];
}
