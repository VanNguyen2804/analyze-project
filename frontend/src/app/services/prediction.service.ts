import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class PredictionService {
  private readonly apiUrl = '/api/analyze/predict';

  constructor(private http: HttpClient) {}

  /**
   * Gọi API phân tích và dự đoán bộ 6 số từ mô hình XGBoost
   */
  getPrediction(): Observable<number[]> {
    return this.http.get<number[]>(this.apiUrl);
  }
}
