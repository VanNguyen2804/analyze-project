export interface SavedLotteryRecord {
  id: number | string;
  drawDate: string; // YYYY-MM-DD
  category: 'MEGA' | 'POWER';
  numbers: number[]; // 6 main numbers
  specialNumber?: number; // Added for category POWER
  createdAt: string;
  note?: string;
}

export interface NumberEntryRequest {
  numbers: number[];
  specialNumber?: number;
  drawDate?: string; // YYYY-MM-DD
  category?: 'MEGA' | 'POWER';
  note?: string;
}
