import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { NumberSelectionReason, PredictionPayload } from '../models/prediction-payload.model';
import { environment } from '../../../environments/environment'; // Import file environment của bạn

@Injectable({
  providedIn: 'root'
})
export class AnalyzeService {
  private apiUrl = `${environment.apiUrl || ''}/api/analyze`;
  private frenchApiUrl = `${environment.apiUrl || ''}/api/french`;

  private categorySource = new BehaviorSubject<string>('MEGA');
  currentCategory$ = this.categorySource.asObservable();

  constructor(private http: HttpClient) { }

  setCategory(category: string) {
    this.categorySource.next(category);
  }

  // ==========================================
  // API XỔ SỐ (PHÂN TÍCH & DỰ ĐOÁN)
  // ==========================================

  getPrediction(category: string, algorithm: string): Observable<PredictionPayload> {
    return this.http.get<PredictionPayload>(`${this.apiUrl}/predict?category=${category}&algorithm=${algorithm}`);
  }

// ĐÃ KHÔI PHỤC: Phân tích 6 số đã sổ (Trang Latest Draw) với 3 tham số
  getOfficialDrawAnalysis(category: string, date: string, algorithm: string): Observable<any> {
    let url = `${this.apiUrl}/official-draw-analysis?category=${category}&algorithm=${algorithm}`;
    if (date && date.trim() !== '') {
      url += `&date=${date}`;
    }
    return this.http.get<any>(url);
  }

  getHistory(category: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/history?category=${category}`);
  }

  checkTickets(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/check-tickets`, payload);
  }

  addOfficialResult(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/add-result`, payload, { responseType: 'text' as 'json' });
  }

  editOfficialResult(id: number, payload: any): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/update-result/${id}`, payload, { responseType: 'text' as 'json' });
  }

  getUserHistory(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/user-history`);
  }

  clearUserHistory(): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/user-history`, { responseType: 'text' as 'json' });
  }

  // ==========================================
  // API TIẾNG PHÁP
  // ==========================================

  getFrenchExercises(category: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.frenchApiUrl}/exercises?category=${category}`);
  }

  submitFrenchAttempt(payload: any): Observable<any> {
    return this.http.post<any>(`${this.frenchApiUrl}/attempt`, payload);
  }

  getFrenchHistory(): Observable<any[]> {
    return this.http.get<any[]>(`${this.frenchApiUrl}/history`);
  }
}