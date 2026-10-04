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

export interface TicketItem {
  id?: number | string;
  ticketIndex?: number;
  numbers: number[];
  specialNumber?: number | null;
  matchedNumbers?: number[];
  missedNumbers?: number[];
  matchedCount?: number;
  matchedSpecial?: boolean;
  prize?: string;
  prizeAmount?: string;
  isWinning?: boolean;
  pairSynergyScore?: number;
  cohesionLevel?: string;
  explanation?: string;
  source?: 'history' | 'manual' | 'ai';
  note?: string;
  checkedAt?: string | Date;
  status?: string;
}

export interface TicketAffinityDetail extends TicketItem {
  ticketIndex: number;
  numbers: number[];
  pairSynergyScore?: number;
  cohesionLevel?: string;
  explanation?: string;
}

export interface NumberRelationshipData {
  summary?: string;
  algorithmName?: string;
  antiScatteringGuarantee?: string;
  topAffinityPairs?: Array<{
    n1: number;
    n2: number;
    pairLabel?: string;
    coOccurrence: number;
    lift: number;
    jaccard: number;
    deltaDiff: number;
    affinityLabel?: string;
    role?: string;
    affinityScore?: number;
  }>;
  topCliques?: Array<{
    size?: number;
    numbers: number[];
    affinityScore: number;
    description: string;
  }>;
  deltaCorrelations?: Array<{
    delta: number;
    frequency: number;
    description: string;
  }>;
  ticketAffinityDetails?: TicketAffinityDetail[];
}

export interface PredictionPayload {
  status: string;
  message?: string;
  category?: string;
  lotteryType?: string;
  tickets?: number[][];
  ticketItems?: TicketItem[];
  generatedTickets?: TicketItem[];
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
  numberRelationships?: NumberRelationshipData;
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
