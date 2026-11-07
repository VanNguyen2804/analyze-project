import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { timeout } from 'rxjs';
import {
  StockMode,
  StockSnapshot,
  StockPositionInput,
  StockPositionResult,
  StockWatchQuote,
} from '../models/stock.model';

@Injectable({ providedIn: 'root' })
export class StockApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl || ''}/api/stocks`;
  watchlist() {
    return this.http.get<StockWatchQuote[]>(`${this.base}/watchlist`).pipe(timeout(25000));
  }
  analyze(symbol: string, mode: StockMode) {
    return this.http
      .get<StockSnapshot>(`${this.base}/${symbol}/analysis`, { params: { mode } })
      .pipe(timeout(40000));
  }
  position(symbol: string, snapshotId: string, input: StockPositionInput) {
    return this.http
      .post<StockPositionResult>(
        `${this.base}/${symbol}/position-analysis`,
        { ...input, purchaseDate: input.purchaseDate || null },
        {
          params: { snapshotId },
        },
      )
      .pipe(timeout(15000));
  }
}
