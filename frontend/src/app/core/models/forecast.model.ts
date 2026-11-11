import { LotteryCategory } from './lottery-category.model';

export type SettlementStatus = 'PENDING' | 'SETTLED' | 'RESULT_UNAVAILABLE';
export interface ForecastSettlement {
  status: SettlementStatus;
  actualNumbers: number[] | null;
  matched: number | null;
  frequencyMatched?: number | null;
  randomMatched?: number | null;
}
export interface ForecastTicket {
  id: string;
  targetDate: string;
  createdAt: string;
  numbers: number[];
  settlement?: ForecastSettlement | null;
}
export interface SavedForecast extends ForecastTicket {
  algorithm: string;
  modelVersion: string;
}
export interface ForecastGroup {
  cohort: string;
  algorithm: string;
  modelVersion: string;
  configurationHash: string;
  evaluatedDraws: number;
  savedForecasts: number;
  pendingOrUnavailable: number;
  matchHistogram: number[];
  averageMatched: number | null;
  frequencyAverageMatched: number | null;
  randomAverageMatched: number | null;
  hit3RatePercent: number | null;
  hit4RatePercent?: number | null;
  hit5RatePercent?: number | null;
  hit4Count?: number;
  hit4Interval95?: number[] | null;
  firstTargetDate?: string;
  lastTargetDate?: string;
  budget?: number;
  evidenceType?: string;
}
export interface ForecastOverview {
  evaluationWindow?: { fromDate: string | null; toDate: string | null };
  enabled: boolean;
  asOf?: string;
  rows: SavedForecast[];
  totalRows: number;
  evaluation: { groups: ForecastGroup[]; excludedInvalidSnapshots?: number };
}
export interface LabOutcome {
  category: LotteryCategory;
  model?: string;
  status: string;
  message?: string;
}
export interface LabRun {
  status: string;
  checkedAt?: string;
  message?: string;
  outcomes?: LabOutcome[];
}
export interface LabHistoricalHits {
  cohort: string;
  cohortTag: string;
  modelVersion: string;
  bestMatched: number;
  hitTickets: number;
  hitDraws: number;
  latestHitDate: string;
}
export interface LabTicket extends ForecastTicket {
  cohort?: string;
  modelVersion?: string;
  pastPerformance?: {
    cohorts?: LabHistoricalHits[];
    bestMatched: number;
    hitTickets: number;
    hitDraws: number;
    latestHitDate: string;
  };
  model: string;
  feedback?: { draws: number; tickets: number; ticketMatchHistogram: number[] } | null;
}
export interface LabProgressLevel {
  threshold: number;
  ticketHits: number;
  ticketPercent: number | null;
  drawHits: number;
  drawPercent: number | null;
  frequencyTicketPercent: number | null;
  randomTicketPercent: number | null;
}
export interface LabPredictionPerformance {
  evaluatedDraws: number;
  evaluatedTickets: number;
  hitTickets: number;
  bestMatched: number;
  latestHitDate: string | null;
  levels: { threshold: number; drawHits: number; drawPercent: number | null }[];
}
export interface LabPredictionGroup {
  model: string;
  modelVersion: string;
  cohort: string;
  targetDate: string;
  tickets: LabTicket[];
  performance: LabPredictionPerformance;
  history: (LabPredictionPerformance & { cohort: string; modelVersion: string })[];
}
export interface LabPredictions {
  category: LotteryCategory;
  targetDate: string;
  asOf: string;
  groups: LabPredictionGroup[];
}
export interface LabProgress {
  evaluationVersion: string;
  evaluatedTickets: number;
  totalMatched: number;
  bestTicketMatched: number;
  averageMatchedPerTicket: number | null;
  frequencyAverageMatched: number | null;
  randomAverageMatched: number | null;
  recent30AverageMatched: number | null;
  previous30AverageMatched: number | null;
  matchHistogram: number[];
  levels: LabProgressLevel[];
  timeline: {
    targetDate: string;
    matched: number;
    cumulativeMatched: number;
    averageMatchedPerTicket: number;
  }[];
}
export interface LabGroup {
  cohortTag?: string;
  feedbackComparison?: {
    pairedDraws: number;
    averageMatched: number | null;
    noFeedbackAverageMatched: number | null;
    difference: number | null;
  } | null;
  probability?: {
    evaluatedDraws: number;
    observations: number;
    brierScore: number | null;
    logLoss: number | null;
    uniformBrierScore: number | null;
    uniformLogLoss: number | null;
    bins: {
      lower: number;
      upper: number;
      count: number;
      predicted: number | null;
      observed: number | null;
    }[];
  };
  feedbackEnabled?: boolean;
  progress?: LabProgress;
  cohort: string;
  model: string;
  modelVersion: string;
  evaluatedDraws: number;
  savedDraws: number;
  ticketHit3Percent: number | null;
  drawHit3Percent: number | null;
  frequencyHit3Percent: number | null;
  randomHit3Percent: number | null;
  recent30Percent: number | null;
  previous30Percent: number | null;
  continuous60: boolean;
  status: 'VALIDATION_CANDIDATE' | 'COLLECTING';
}
export interface LabOverview {
  highlights?: LabTicket[];
  enabled: boolean;
  lastRun: LabRun;
  budget: number;
  page: number;
  totalRows: number;
  rows: LabTicket[];
  groups: LabGroup[];
}
export interface ForecastMutationResult {
  status?: string;
  message?: string;
}
