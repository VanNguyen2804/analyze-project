import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap, map } from 'rxjs';
import { CategoryService } from './category.service';
import { AlgorithmOption } from '../models/algorithm.model';
import { PredictionPayload } from '../models/prediction-payload.model';
import { DrawReport, AnalyzedTicketGroup } from '../models/draw-report.model';
import { environment } from '../../../environments/environment'; // Import file environment của bạn
import { PageJob, PageJobApiService } from './page-job-api.service';

@Injectable({
  providedIn: 'root',
})
export class AnalyzeService {
  private readonly http = inject(HttpClient);
  private readonly jobs = inject(PageJobApiService);

  private apiUrl = `${environment.apiUrl || ''}/api/analyze`;

  private readonly categories = inject(CategoryService);
  readonly currentCategory$ = this.categories.category$;
  readonly category = this.categories.category;

  setCategory(category: string) {
    this.categories.setCategory(category);
  }

  // ==========================================
  // API XỔ SỐ (PHÂN TÍCH & DỰ ĐOÁN)
  // ==========================================

  getPrediction(
    category: string,
    algorithm: string,
    algorithm2?: string,
  ): Observable<PredictionPayload> {
    return this.jobs.follow(
      this.http.post<PredictionPayload | PageJob<PredictionPayload>>(
        `${environment.apiUrl || ''}/api/prediction-workspace/suggestions`,
        {
          category,
          algorithm,
          algorithm2: algorithm2 || 'bayesian_graph',
        },
        { params: { async: true } },
      ),
    );
  }

  getAlgorithms(): Observable<AlgorithmOption[]> {
    return this.http.get<AlgorithmOption[]>(`${this.apiUrl}/algorithms`);
  }
  getLatestDraw(
    category: string,
  ): Observable<{ drawDate: string; numbers: number[]; specialNumber: number | null }> {
    return this.http.get<{ drawDate: string; numbers: number[]; specialNumber: number | null }>(
      `${this.apiUrl}/latest-draw`,
      { params: { category } },
    );
  }

  getDrawReport(
    category: string,
    date: string,
    algorithm: string,
    evidenceId = '',
  ): Observable<DrawReport> {
    return this.http.get<DrawReport>(`${this.apiUrl}/draw-report`, {
      params: {
        category,
        algorithm,
        ...(date ? { date } : {}),
        ...(evidenceId ? { evidenceId } : {}),
      },
    });
  }

  evaluateTickets(
    category: string,
    date: string,
    tickets: number[][],
  ): Observable<AnalyzedTicketGroup> {
    return this.http.post<AnalyzedTicketGroup>(`${this.apiUrl}/evaluate-tickets`, {
      category,
      date,
      tickets,
    });
  }

  // ĐÃ KHÔI PHỤC: Phân tích 6 số đã sổ (Trang Latest Draw) với 3 tham số
  getOfficialDrawAnalysis(category: string, date: string, algorithm: string): Observable<any> {
    let url = `${this.apiUrl}/official-draw-analysis?category=${category}&algorithm=${algorithm}`;
    if (date && date.trim() !== '') {
      url += `&date=${date}`;
    }
    return this.http.get<any>(url);
  }

  private numbersUrl = `${environment.apiUrl || ''}/api/numbers`;

  getHistory(category: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/history?category=${category}`);
  }

  getStoredHistory(category: string): Observable<any[]> {
    return this.http.get<any[]>(this.numbersUrl, { params: { category } });
  }

  checkTickets(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/check-tickets`, payload);
  }

  private verifySaved(saved: any): Observable<any> {
    if (!saved?.id || !saved?.category || !saved?.drawDate) {
      throw new Error('Máy chủ chưa trả về bản ghi đã lưu.');
    }
    return this.http.get<any[]>(this.numbersUrl, {
      params: { category: saved.category, date: saved.drawDate },
    }).pipe(map(rows => {
      const row = rows.find(r => r.id === saved.id);
      if (!row || JSON.stringify(row.numbers) !== JSON.stringify(saved.numbers)
          || row.specialNumber !== saved.specialNumber) {
        throw new Error('Chưa xác nhận được dữ liệu sau khi lưu. Vui lòng tải lại.');
      }
      return row;
    }));
  }

  addOfficialResult(payload: any): Observable<any> {
    return this.http.post<any>(this.numbersUrl, payload).pipe(
      switchMap(saved => this.verifySaved(saved)),
    );
  }

  editOfficialResult(id: number, payload: any): Observable<any> {
    return this.http.put<any>(`${this.numbersUrl}/${id}`, payload).pipe(
      switchMap(saved => this.verifySaved(saved)),
      map(() => 'Đã đọc lại và xác nhận kết quả đã lưu.'),
    );
  }
  getUserHistory(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/user-history`);
  }

  clearUserHistory(): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/user-history`, { responseType: 'text' as 'json' });
  }

  // ==========================================
  // API ĐỐI SOÁT & KIỂM THỬ CÁC KỲ TRƯỚC (HISTORICAL BACKTEST)
  // ==========================================
  get5DrawsReconciliation(
    category?: string,
    algorithm?: string,
    limit: number = 5,
    date?: string,
  ): Observable<any> {
    const cat = category || 'POWER';
    const alg = algorithm || 'deep_stacking';
    let url = `${this.apiUrl}/reconcile-5-draws?category=${cat}&algorithm=${alg}&limit=${limit}`;
    if (date && date.trim()) {
      url += `&date=${encodeURIComponent(date.trim())}`;
    }
    return this.http.get<any>(url);
  }

  getReconcile5Draws(category?: string, limit: number = 5): Observable<any> {
    return this.get5DrawsReconciliation(category, 'deep_stacking', limit);
  }

  // ==========================================
  // API SIÊU THAM SỐ THUẬT TOÁN (HYPERPARAMETERS TABLE)
  // ==========================================
  getHyperparameters(category?: string): Observable<any[]> {
    let url = `${this.apiUrl}/hyperparameters`;
    if (category && category !== 'ALL') {
      url += `?category=${category}`;
    }
    return this.http.get<any[]>(url);
  }

  getLatestHyperparameters(category?: string): Observable<any> {
    let url = `${this.apiUrl}/hyperparameters/latest`;
    if (category && category !== 'ALL') {
      url += `?category=${category}`;
    }
    return this.http.get<any>(url);
  }

  saveHyperparameters(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/hyperparameters`, payload);
  }

  updateAlgorithm(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/update-algorithm`, payload);
  }

  activateHyperparameter(id: number): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/hyperparameters/activate/${id}`, {});
  }

  // ==========================================
  // API SO SÁNH KẾT QUẢ KỲ MEGA VÀ POWER GẦN ĐÂY & UPDATE THUẬT TOÁN v2.1.0
  // ==========================================
  getCrossGameComparison(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/cross-game-comparison`);
  }
}
