import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PredictionPayload {
  status: string;
  message?: string;
  lotteryType?: string;
  tickets?: number[][];
}

@Injectable({ providedIn: 'root' })
export class AnalyzeService {
  private readonly apiUrl = (environment.apiUrl ? environment.apiUrl : '') + '/api/analyze';

  constructor(private http: HttpClient) { }

  getPrediction(): Observable<PredictionPayload> {
    return this.http.get<PredictionPayload>(`${this.apiUrl}/predict`);
  }
}