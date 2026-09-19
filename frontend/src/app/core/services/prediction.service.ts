import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface NumberScoreDetail {
  number: number;
  probabilityPercent: number;
  frequency: number;
  drawGap: number;
  tag: string; // 'SỐ NÓNG' | 'LÔ GAN' | 'CẶP ĐI KÈM' | 'CÂN BẰNG'
}

export interface PredictionResponse {
  category: 'MEGA' | 'POWER';
  numbers: number[];
  totalDrawsAnalyzed: number;
  hotNumbers: number[];
  coldNumbers: number[];
  frequentPairs: string[];
  oddEvenRatio: string;
  analysisSummary: string;
  details: NumberScoreDetail[];
}

@Injectable({
  providedIn: 'root'
})
export class PredictionService {
  private readonly apiUrl = (environment.apiUrl ? environment.apiUrl : '') + '/api/analyze/predict';

  constructor(private http: HttpClient) {}

  /**
   * Phân tích theo từng dãy số theo ngày cho từng category (MEGA hoặc POWER)
   * và đề xuất bộ 6 số tối ưu cho category đó.
   */
  getPrediction(category: 'MEGA' | 'POWER' = 'MEGA'): Observable<PredictionResponse> {
    return this.http.get<PredictionResponse>(`${this.apiUrl}?category=${category}`);
  }
}
