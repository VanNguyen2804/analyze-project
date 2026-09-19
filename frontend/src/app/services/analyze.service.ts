import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AnalyzeService {
  private apiUrl = 'http://localhost:8080/api/analyze';

  constructor(private http: HttpClient) { }

  getPrediction(): Observable<number[]> {
    return this.http.get<number[]>(`${this.apiUrl}/predict`);
  }
}
