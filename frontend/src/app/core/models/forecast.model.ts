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
}
export interface ForecastOverview {
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
export interface LabTicket extends ForecastTicket {
  model: string;
}
export interface LabGroup {
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
