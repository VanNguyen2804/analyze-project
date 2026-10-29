export interface NumberDetail {
  number: number;
  rank: number | null;
  score: number | null;
  scoreType: string | null;
  frequency: number | null;
  drawGap: number | null;
  evidenceId: string | null;
  note: string;
  features: Record<string, number>;
}
export interface DrawEvidence {
  id: string;
  source: string;
  algorithm: string;
  createdAt: string;
  modelVersion: string | null;
  codeVersion: string | null;
  configurationHash: string | null;
  trainingThrough: string;
  historyHash: string;
  historyCount: number;
  numbers: number[];
  explanationStatus: 'AVAILABLE' | 'RANKING_ONLY' | 'NOT_SAVED';
  numberDetails: NumberDetail[];
  selectionPolicy: string;
  selectionConstraints: { primaryWeight: number | null; keep: number[]; exclude: number[] };
  scoringPolicy: string | null;
  modelWeights: Record<string, number>;
}
export interface AnalyzedTicket {
  id: string;
  source: string;
  sourceId: string | null;
  label: string;
  numbers: number[];
  originalNumbers: number[] | null;
  edited: boolean;
  matchedNumbers: number[];
  missedNumbers: number[];
  matchedCount: number;
  matchedSpecial: boolean;
  prize: string;
}
export interface TicketPortfolio {
  enteredTicketCount: number;
  ticketCount: number;
  duplicateTicketCount: number;
  uniqueNumberCount: number;
  uniqueNumbers: number[];
  rawMatchedOccurrences: number;
  coverage: number[];
  coverageCount: number;
  coveragePercent: number;
  bestMatched: number | null;
  averageMatched: number | null;
  matchHistogram: number[];
  overlaps: number[][];
  numberUsage: Record<string, number>;
}
export interface AnalyzedTicketGroup {
  id: string;
  label: string;
  timing: string;
  tickets: AnalyzedTicket[];
  portfolio: TicketPortfolio;
  frequency: TicketPortfolio | null;
  random: TicketPortfolio | null;
  invalidTicketCount: number;
  comparison?: {
    pairedTicketCount: number;
    originalAverageMatched: number;
    finalAverageMatched: number;
    averageDifference: number;
  } | null;
}
export interface DrawEvaluation {
  cohort: string;
  source: string;
  algorithm: string;
  modelVersion: string | null;
  ticketBudget: number;
  evaluatedDraws: number;
  firstDate: string | null;
  lastDate: string | null;
  averageMatched: number | null;
  bestMatchedAverage: number | null;
  coverageAverage: number | null;
  hit3RatePercent: number | null;
  hit4RatePercent: number | null;
  frequencyAverageMatched: number | null;
  randomAverageMatched: number | null;
  pairedDraws: number;
  pairedDifferenceVsFrequency: number | null;
  approximate95PercentInterval: number[] | null;
  evidenceStatus: string;
}
export interface DrawReport {
  schemaVersion: string;
  context: { category: string; drawDate: string | null; algorithm: string; generatedAt: string };
  status: string;
  message: string | null;
  draw: {
    numbers: number[];
    specialNumber: number | null;
    fingerprint: string;
    quality: string;
    sourceUrl: string | null;
    updatedAt: string | null;
  } | null;
  evidence: DrawEvidence[];
  selectedEvidenceId: string | null;
  numberDetails: NumberDetail[];
  ticketGroups: AnalyzedTicketGroup[];
  evaluation: DrawEvaluation[];
  warnings: string[];
}
