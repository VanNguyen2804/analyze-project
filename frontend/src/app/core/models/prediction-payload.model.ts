export interface PredictionPayload {
  status: string;
  message?: string;
  category?: string;
  lotteryType?: string;
  tickets?: number[][];
  details?: NumberScoreDetail[]; // Thêm mảng details hiển thị bóng số
  
  // Bổ sung thêm các field khác từ Backend để tránh lỗi khi map dữ liệu ra HTML
  numbers?: number[];
  specialNumber?: number;
  totalDrawsAnalyzed?: number;
  hotNumbers?: number[];
  coldNumbers?: number[];
  specialHotNumbers?: number[];
  frequentPairs?: string[];
  jackpot2Pairs?: string[];
  oddEvenRatio?: string;
  selectionReasons?: any[]; // Bạn có thể thay 'any' bằng 'NumberSelectionReason' nếu đã import
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