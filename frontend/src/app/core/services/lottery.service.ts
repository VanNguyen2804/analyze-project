import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SavedLotteryRecord, NumberEntryRequest } from '../models/lottery-number.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class LotteryService {
  private apiUrl = (environment.apiUrl ? environment.apiUrl : '') + '/api/numbers';

  constructor(private http: HttpClient) {}

  getAll(date?: string, category?: string): Observable<SavedLotteryRecord[]> {
    let params = new HttpParams();
    if (date && date.trim()) {
      params = params.set('date', date.trim());
    }
    if (category && category.trim()) {
      params = params.set('category', category.trim().toUpperCase());
    }
    return this.http.get<SavedLotteryRecord[]>(this.apiUrl, { params });
  }

  save(request: NumberEntryRequest): Observable<SavedLotteryRecord> {
    return this.http.post<SavedLotteryRecord>(this.apiUrl, request);
  }

  delete(id: number | string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
