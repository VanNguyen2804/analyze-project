import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class PredictionService {
  private readonly apiUrl = (environment.apiUrl ? environment.apiUrl : '') + '/api/analyze/predict';

  constructor(private http: HttpClient) {}

  /**
   * Gọi API phân tích và dự đoán bộ 6 số từ mô hình XGBoost
   * Hỗ trợ category: 'MEGA' (1-45) hoặc 'POWER' (1-55)
   */
  getPrediction(category: 'MEGA' | 'POWER' = 'MEGA'): Observable<number[]> {
    return this.http.get<number[]>(`${this.apiUrl}?category=${category}`);
  }
}
