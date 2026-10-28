export interface WorkspaceTicket {
  id: string;
  source: 'ALGORITHM' | 'COMBINED' | 'AI_LAB';
  label: string;
  numbers: number[];
  labOrigins?: { id: string; model: string; createdAt: string; modelVersion: string }[];
}
export interface WorkspaceSuggestion {
  id: string;
  category: string;
  targetDate: string;
  createdAt: string;
  status: string;
  message?: string;
  trainingThrough?: string;
  historyCount?: number;
  historyHash?: string;
  expectedRevision: number;
  primaryAlgorithm: string;
  secondaryAlgorithm: string;
  primaryWeight: number;
  keep: number[];
  exclude: number[];
  labStatus: string;
  labPolicy: string;
  tickets: WorkspaceTicket[];
}
export interface WorkspaceMetrics {
  ticketCount: number;
  matches: number[];
  bestMatched: number;
  coverageCount: number;
  coverage: number[];
  averageMatched: number;
}
export interface WorkspaceBatch {
  id: string;
  category: string;
  targetDate: string;
  revision: number;
  committedAt: string;
  active: boolean;
  tickets: { numbers: number[]; originalNumbers?: number[]; label: string; edited: boolean }[];
  settlement?: {
    status: string;
    actualNumbers?: number[];
    user?: WorkspaceMetrics;
    frequency?: WorkspaceMetrics;
    random?: WorkspaceMetrics;
    originalSelected?: WorkspaceMetrics;
    editedSelected?: WorkspaceMetrics;
    algorithmSuggestions?: WorkspaceMetrics;
    labSuggestions?: WorkspaceMetrics;
  };
}
