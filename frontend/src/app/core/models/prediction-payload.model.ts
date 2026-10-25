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

export interface PureAlgorithmTicket {
  numbers: number[];
  specialNumber?: number;
  confidence?: number;
  algorithmId?: string;
  algorithmName?: string;
  formulaNote?: string;
  rationale?: string;
  details?: Array<{
    number: number;
    probabilityPercent: number;
    drawGap?: number;
    frequency?: number;
    tag?: string;
    title?: string;
    reason?: string;
  }>;
}

export interface TransformedSequence {
  id: string;
  name: string;
  badge: string;
  badgeClass: string;
  icon?: string;
  numbers: number[];
  specialNumber?: number;
  composition?: string;
  confidence?: number;
  deltaFormula?: string;
  transformationRule?: string;
  keyShifts?: string[];
}

export interface AlgorithmResultItem {
  id?: string;
  name: string;
  badge?: string;
  badgeClass?: string;
  rankBadge?: string;
  icon?: string;
  accuracyScore?: number | null;
  hitRate?: string;
  numbers: number[];
  specialNumber?: number;
  composition?: string;
  formulaNote?: string;
  rationale?: string;
  sharedNumbers?: number[];
  details?: Array<{
    number: number;
    probabilityPercent: number;
    drawGap?: number;
    frequency?: number;
    tag?: string;
    isShared?: boolean;
  }>;
}

export interface PredictionPayload {
  relationshipLearning?: RelationshipLearning;
  status: string;
  message?: string;
  category?: string;
  lotteryType?: string;
  tickets?: number[][];
  ticketItems?: TicketItem[];
  generatedTickets?: TicketItem[];
  pureNumbers?: number[];
  pureAlgorithmTicket?: PureAlgorithmTicket;
  primaryAlgorithmResult?: AlgorithmResultItem;
  secondaryAlgorithmResult?: AlgorithmResultItem;
  combinedAlgorithmResult?: AlgorithmResultItem;
  transformedSequences?: TransformedSequence[];
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
  recommendations?: any;
  aiRecommendation?: any;
  targetDrawDate?: string;
  targetDayName?: string;
  targetDrawDateFormatted?: string;
  baseDrawDate?: string;
  baseDrawDateFormatted?: string;
  isTargetDrawToday?: boolean;
  deviationVariables?: DeviationVariable[];
  neighborDriftAnalysis?: NeighborDriftAnalysis;
  commonPatternsAnalysis?: CommonPatternsAnalysis;
  realityVsAiAnalysis?: any;
}

export interface RelationshipLearning {
  modelVersion: string;
  category: string;
  asOfDate: string | null;
  trainingDraws: number;
  forecastPolicy: 'frequency_fallback' | 'learned_mixture';
  skippedGapTransitions: number;
  predictedNumbers: number[];
  patterns: Array<{ id: string; name: string; weightPercent: number; support: number; occurrences: number | null }>;
  topTransitions: Array<{ fromNumber: number; toNumber: number; support: number; occurrences: number; conditionalPercent: number }>;
  verifiedWinningTickets?: Array<{ id?: number; drawDate: string; numbers: number[]; matchedNumbers: number[]; matchedCount: number;
    historicalLinks?: Array<{ drawDate: string; sharedNumbers: number[]; daysApart: number }> }>;
  evaluation: {
    method: string;
    evaluatedDraws: number;
    averageMatched: number | null;
    baselineAverageMatched: number | null;
    hit3RatePercent: number | null;
    brierScore: number | null;
    baselineBrierScore: number | null;
  };
}

export interface CommonPatternDetail {
  rule?: string;
  balanceRatio?: string;
  optimalRange?: string;
  occurrencesCount?: number;
  percentage: number;
  insight: string;
}

export interface CommonPatternsAnalysis {
  evaluatedDraws: number;
  amplitude1Pattern: CommonPatternDetail;
  parityPattern: CommonPatternDetail;
  zoneDistributionPattern: CommonPatternDetail;
  tailModuloPattern: CommonPatternDetail;
  consecutivePairPattern: CommonPatternDetail;
  sumRangePattern: CommonPatternDetail;
}

export interface NeighborDriftCandidate {
  originalNumber: number;
  leftHedging: number;
  rightHedging: number;
  preferredShift: '-1' | '+1' | 'both';
  riskScore: number;
  riskLevel: 'VERY_HIGH' | 'HIGH' | 'MEDIUM';
  reason: string;
}

export interface NeighborDriftAnalysis {
  isSimilarCycleTo22Oct: boolean;
  totalDriftDrawsCount: number;
  severeDriftDrawsCount: number;
  historicalRatioPercent: number;
  powerDriftRatioPercent: number;
  megaDriftRatioPercent: number;
  keyHistoricalDraws: Array<{
    category: string;
    drawDate: string;
    driftCount: number;
    driftBalls: number[];
    note: string;
  }>;
  cycleDiagnosis: string;
  riskCandidates: NeighborDriftCandidate[];
  hedgingTicketNumbers: number[];
  hedgingRationale: string;
}

export interface DeviationVariable {
  id?: number;
  category: string;
  baseDrawDate: string;
  targetDrawDate: string;
  aiPredictedNumber: number;
  actualNumber: number;
  variableDelta: number;
  variableType: string;
  patternName: string;
  probabilityShift: number;
  transformationRule: string;
  createdAt?: string;
  note?: string;
  generatedNextNumber?: number;
  savedInDb?: boolean;
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

export interface SequenceTransitionLink {
  fromDrawDate: string;
  toDrawDate: string;
  fromNumber: number;
  toNumber: number;
  fromIndex: number;
  toIndex: number;
  type: 'REPEAT' | 'DRIFT' | 'SAME_TAIL' | 'SPECIAL_MIGRATION';
  typeLabel: string;
  color: string;
  delta: number;
  description: string;
}

export interface DrawSequenceNode {
  drawId: number;
  drawDate: string;
  drawDateFormatted: string;
  numbers: number[];
  specialNumber?: number;
  oddEvenRatio: string;
  sum: number;
}

export interface NetworkGraphNode {
  number: number;
  frequency: number;
  x: number;
  y: number;
  angle: number;
  degree: number;
}

export interface NetworkGraphEdge {
  n1: number;
  n2: number;
  count: number;
  lift: number;
  jaccard: number;
  pathD: string;
  color: string;
  strokeWidth: number;
}

export interface NumberGapPressureItem {
  number: number;
  currentGap: number;
  lastDrawDate: string;
  expectedInterval: number;
  pressurePercent: number;
  frequency: number;
  zone: 'HOT' | 'CYCLE' | 'PRESSURE' | 'OVERDUE';
  zoneLabel: string;
  zoneBadgeClass: string;
  zScore: number;
}

export interface DeltaSpacingDistributionItem {
  deltaRange: string;
  minDelta: number;
  maxDelta: number;
  count: number;
  percent: number;
  description: string;
  isOptimal: boolean;
}

export interface SumDistributionItem {
  sumRange: string;
  minSum: number;
  maxSum: number;
  count: number;
  percent: number;
  isPeak: boolean;
}

export interface ParityDistributionItem {
  ratio: string;
  count: number;
  percent: number;
  isDominant: boolean;
}

export interface CategoryLinkageChartData {
  category: 'POWER' | 'MEGA';
  categoryTitle: string;
  badgeClass: string;
  totalDraws: number;
  maxLimit: number;
  recentDraws: DrawSequenceNode[];
  transitionLinks: SequenceTransitionLink[];
  repeatRate: number;
  drift1Rate: number;
  sameTailRate: number;
  // Các biểu đồ phục vụ trực tiếp nhận định dự đoán
  gapPressureList: NumberGapPressureItem[];
  topPressureNumbers: NumberGapPressureItem[];
  topHotNumbers: NumberGapPressureItem[];
  deltaDistributions: DeltaSpacingDistributionItem[];
  sumDistributions: SumDistributionItem[];
  parityDistributions: ParityDistributionItem[];
  optimalSumRange: string;
  recommendedDeltaSpacing: string;
}

