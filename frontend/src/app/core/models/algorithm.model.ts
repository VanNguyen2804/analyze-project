export interface AlgorithmOption {
  id: string;
  name: string;
  icon: string;
  rankBadge: string;
  formula: string;
  description: string;
  highlight: string;
}

export interface AlgorithmHyperparameter {
  id: number;
  version: string;
  category?: string;
  drawDate?: string;
  model?: string;
  readmeContent?: string;
  hyperparametersJson?: string;
  hyperparameters?: Record<string, unknown>;
  note?: string;
  createdAt?: string;
}
