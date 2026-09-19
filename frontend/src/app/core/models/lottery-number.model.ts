export interface SavedLotteryRecord {
  id: number | string;
  drawDate: string; // YYYY-MM-DD
  category: 'MEGA' | 'POWER';
  numbers: number[];
  createdAt: string;
  note?: string;
}

export interface NumberEntryRequest {
  numbers: number[];
  drawDate?: string; // YYYY-MM-DD
  category?: 'MEGA' | 'POWER';
  note?: string;
}
