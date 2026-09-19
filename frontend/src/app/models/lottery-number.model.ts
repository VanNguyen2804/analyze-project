export interface SavedLotteryRecord {
  id: number | string;
  numbers: number[];
  createdAt: string;
  note?: string;
}

export interface NumberEntryRequest {
  numbers: number[];
  note?: string;
}
