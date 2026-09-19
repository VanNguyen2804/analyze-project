import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SavedLotteryRecord, NumberEntryRequest } from '../models/lottery-number.model';

@Injectable({
  providedIn: 'root'
})
export class LotteryService {
  private readonly apiUrl = '/api/numbers';

  constructor(private http: HttpClient) {}

  getAll(): Observable<SavedLotteryRecord[]> {
    return this.http.get<SavedLotteryRecord[]>(this.apiUrl);
  }

  save(request: NumberEntryRequest): Observable<SavedLotteryRecord> {
    return this.http.post<SavedLotteryRecord>(this.apiUrl, request);
  }

  delete(id: number | string): Observable<{ success: boolean; message?: string }> {
    return this.http.delete<{ success: boolean; message?: string }>(`${this.apiUrl}/${id}`);
  }
}
