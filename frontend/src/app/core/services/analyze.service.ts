import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { PredictionPayload } from '../models/prediction-payload.model';


@Injectable({
  providedIn: 'root'
})
export class AnalyzeService {
  private apiUrl = 'http://localhost:8080/api/analyze';

  // Quản lý trạng thái Category toàn cục
  private categorySource = new BehaviorSubject<string>('MEGA');
  currentCategory$ = this.categorySource.asObservable();

  constructor(private http: HttpClient) { }

  setCategory(category: string) {
    this.categorySource.next(category);
  }

  // API 1: Phân tích 10 số tiềm năng (Dự đoán XGBoost + Wheeling System)
 // ĐÃ KHÔI PHỤC: Dự đoán 10 số, nhận đủ 2 tham số category và algorithm
  getPrediction(category: string, algorithm: string): Observable<PredictionPayload> {
    return this.http.get<PredictionPayload>(`${this.apiUrl}/predict?category=${category}&algorithm=${algorithm}`);
  }
  
  // API 2: Phân tích 6 số đã trúng thưởng (Nhận đủ 3 tham số: category, date, algorithm)
  getOfficialDrawAnalysis(category: string, date: string, algorithm: string): Observable<any> {
    let url = `${this.apiUrl}/official-draw-analysis?category=${category}&algorithm=${algorithm}`;
    if (date && date.trim() !== '') {
      url += `&date=${date}`;
    }
    return this.http.get<any>(url);
  }

  // API 3: Lấy danh sách lịch sử kỳ quay từ Database
  getHistory(category: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/history?category=${category}`);
  }

  // API 4: Đối chiếu vé của người dùng
  checkTickets(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/check-tickets`, payload);
  }

  // API 5: Nạp kết quả xổ số mới vào Database
  addOfficialResult(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/add-result`, payload, { responseType: 'text' as 'json' });
  }

  // API 6: Chỉnh sửa kết quả xổ số cũ
  editOfficialResult(id: number, payload: any): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/update-result/${id}`, payload, { responseType: 'text' as 'json' });
  }

  // API 7: Lấy lịch sử dò vé cá nhân
  getUserHistory(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/user-history`);
  }

  // API 8: Xóa lịch sử dò vé cá nhân
  clearUserHistory(): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/user-history`, { responseType: 'text' as 'json' });
  }
}