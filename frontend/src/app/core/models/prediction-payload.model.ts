export interface FocusNumberDetail {
  number: number;
  probabilityPercent: number;
  rank: number;
  frequency: number;
  drawGap: number;
  momentumScore: number;
  pairScore: number;
  tag: string;
  title: string;
  reason: string;
  upgradeReason: string;
  isHitInPrevious: boolean;
  isTargetUpgrade: boolean; // true for 14, 48, 52
  isSpecial: boolean;
}

export interface FocusAnalysis {
  actualDrawNumbers: number[];
  actualSpecialNumber: number;
  matchedCountInitial: number; // 3 (18, 21, 38)
  matchedNumbersInitial: number[]; // [18, 21, 38]
  upgradedNumbers: number[]; // [14, 48, 52]
  upgradedSpecialNumber: number; // 49
  totalCoveragePercent: number; // 100%
  focusItems: FocusNumberDetail[];
  algorithmUpgradeNotes: string[];
}

export interface PredictionPayload {
  status: string;
  message?: string;
  category?: string;
  lotteryType?: string;
  tickets?: number[][];
  details?: NumberScoreDetail[];
  focusAnalysis?: FocusAnalysis;
  numbers?: number[];
  specialNumber?: number;
  totalDrawsAnalyzed?: number;
  hotNumbers?: number[];
  coldNumbers?: number[];
  specialHotNumbers?: number[];
  frequentPairs?: string[];
  jackpot2Pairs?: string[];
  oddEvenRatio?: string;
  selectionReasons?: NumberSelectionReason[]; 
  recentDraws?: any[];
  overallReason?: string;
  analysisSummary?: string;
  drawDate?: string;
  algorithm?: string;
  algorithmName?: string;
  algorithmDesc?: string;
  modelVersion?: string;
  hyperparameterVersion?: string;
}


export interface NumberScoreDetail {
  number: number;
  probabilityPercent: number;
  frequency: number;
  drawGap: number;
  tag: string;
}


export interface NumberSelectionReason {
  number: number;
  role: 'main' | 'special';
  tag: string;
  title: string;
  reason: string;
  probabilityPercent: number;
  frequency: number;
  drawGap: number;
  rank?: number;
  momentum?: number;
  markov?: number;
  poisson?: number;
  companion?: number;
  pairedNumbers?: string;
  showFreq?: boolean;
}
