import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { timeout } from 'rxjs';
import { PageJob, PageJobApiService } from './page-job-api.service';
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
  private readonly jobs = inject(PageJobApiService);
  private readonly base = `${environment.apiUrl || ''}/api/stocks`;
  watchlist() {
    return this.jobs.follow(
      this.http.get<StockWatchQuote[] | PageJob<StockWatchQuote[]>>(`${this.base}/watchlist`, {
        params: { async: true },
      }),
    );
  }
  analyze(symbol: string, mode: StockMode) {
    return this.jobs.follow(
      this.http.get<StockSnapshot | PageJob<StockSnapshot>>(`${this.base}/${symbol}/analysis`, {
        params: { mode, async: true },
      }),
    );
  }
  position(symbol: string, snapshotId: string, input: StockPositionInput) {
    return this.http
      .post<StockPositionResult>(
        `${this.base}/${symbol}/position-analysis`,
        !input.plan && input.maxLossPercent == null
          ? { purchasePrice: input.purchasePrice, quantity: input.quantity }
          : { ...input, purchaseDate: input.purchaseDate || null },
        {
          params: { snapshotId },
        },
      )
      .pipe(timeout(15000));
  }
}
