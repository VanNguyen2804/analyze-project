import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PredictionPayload } from '../models/prediction-payload.model';


@Injectable({ providedIn: 'root' })
export class AnalyzeService {
  private readonly apiUrl = (environment.apiUrl ? environment.apiUrl : '') + '/api/analyze';
// Khởi tạo BehaviorSubject với giá trị mặc định là MEGA
  private categorySource = new BehaviorSubject<string>('MEGA');
  constructor(private http: HttpClient) { }

  // Biến observable để các component khác (như Prediction) subscribe vào
  currentCategory$ = this.categorySource.asObservable();

  // Hàm để Header gọi khi người dùng bấm chọn danh mục
  setCategory(category: string) {
    this.categorySource.next(category);
  }

// Cập nhật hàm getPrediction để nhận tham số category
  getPrediction(category: string): Observable<PredictionPayload> {
    return this.http.get<PredictionPayload>(`${this.apiUrl}/predict?category=${category}`);
  }

  checkTickets(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/check-tickets`, payload);
  }

  addOfficialResult(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/add-result`, payload, { responseType: 'text' as 'json' });
  }
}