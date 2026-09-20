export interface PredictionPayload {
  status: string;
  message?: string;
  lotteryType?: string;
  tickets?: number[][];
}