export interface NumberScoreDetail {
  number: number;
  probabilityPercent: number;
  frequency: number;
  drawGap: number;
  tag: string;
}

export interface SpecialNumberDetail {
  number: number;
  probabilityPercent: number;
  specialFrequency: number;
  totalFrequency: number;
  drawGap: number;
  tag: string;
  description: string;
}

export interface PredictionResponse {
  category: 'MEGA' | 'POWER';
  numbers: number[];
  specialNumber?: number; // Added for category POWER
  specialNumberDetail?: SpecialNumberDetail;
  specialHotNumbers?: number[];
  jackpot2Pairs?: string[];
  totalDrawsAnalyzed: number;
  hotNumbers: number[];
  coldNumbers: number[];
  frequentPairs: string[];
  oddEvenRatio: string;
  details: NumberScoreDetail[];
  analysisSummary: string;
  timestamp?: string;
  model?: string;
}
