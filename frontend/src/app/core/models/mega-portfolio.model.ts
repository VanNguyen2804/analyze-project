export type MegaPortfolioArmId = 'portfolio' | 'wheel' | 'frequency' | 'random';

export interface MegaPortfolioContext {
  targetDate: string;
  tickets: number[][];
}

export interface MegaPortfolioMetrics {
  ticketCount: number;
  matches: number[];
  matchedNumbers: number[][];
  bestMatched: number;
  coverage: number[];
  coverageCount: number;
  averageMatched: number;
  hit3: boolean;
  hit4: boolean;
}

export interface MegaPortfolioArm {
  id: MegaPortfolioArmId;
  label: string;
  tickets: number[][];
  uniqueNumbers: number;
  maxPairOverlap: number;
  metrics: MegaPortfolioMetrics | null;
}

export interface MegaPortfolioRow {
  id: string;
  targetDate: string;
  createdAt: string;
  trainingThrough: string;
  codeVersion: string;
  modelVersion?: string;
  status: 'PENDING' | 'SETTLED' | 'RESULT_UNAVAILABLE';
  actualNumbers: number[] | null;
  arms: MegaPortfolioArm[];
}

export interface MegaPortfolioGroup {
  cohort: string;
  codeVersion: string;
  modelVersion?: string;
  savedDraws: number;
  evaluatedDraws: number;
  firstTargetDate: string;
  lastTargetDate: string;
  status: 'COLLECTING';
  arms: {
    id: MegaPortfolioArmId;
    label: string;
    hit3Draws: number;
    hit3Percent: number | null;
    averageMatched: number | null;
    averageResultCoverage: number | null;
  }[];
}

export interface MegaPortfolioRun {
  status: string;
  message?: string | null;
  checkedAt?: string;
}

export interface MegaPortfolioOverview {
  version: string;
  category: 'MEGA';
  budget: number;
  enabled: boolean;
  targetDate: string;
  lastRun: MegaPortfolioRun;
  current: MegaPortfolioRow | null;
  page: number;
  totalRows: number;
  excludedInvalidSnapshots: number;
  rows: MegaPortfolioRow[];
  groups: MegaPortfolioGroup[];
}
