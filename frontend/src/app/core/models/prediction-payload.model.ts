export interface PredictionPayload {
  status: string;
  message?: string;
  category?: string;
  lotteryType?: string;
  algorithm?: string;
  algorithmName?: string;
  algorithmDesc?: string;
  tickets?: number[][];
  details?: NumberScoreDetail[];
  
  // Bổ sung thêm các field từ Backend
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
  analysisSummary?: string;
  overallReason?: string;
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
}
