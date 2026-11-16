export interface TradingPlan { ema200?: number; support: number; resistance: number; atr14: number; id: string; title: string; duration: string; entryLow: number; entryHigh: number; stop: number; target1: number; target2: number; profit1: number; profit2: number; riskAmount: number; rewardRisk: number; status: string; method: string; }
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
  tradingPlans(symbol: string, input: StockPositionInput, calculation: string = 'SR_ATR') { return this.http.post<{plans: TradingPlan[]; message?: string; asOf?: number}>(this.base + '/' + symbol + '/trading-plans', { ...input, purchaseDate: input.purchaseDate || null }, { params: { calculation } }); }
  checkpoints(symbol: string) { return this.http.get<Array<{checkpoint: string; capturedAt: string; swing: StockSnapshot; intraday: StockSnapshot; swingPosition?: StockPositionResult; intradayPosition?: StockPositionResult; longTermNote: string}>>(this.base + '/' + symbol + '/checkpoints'); }
  savedPositions() { return this.http.get<Record<string, StockPositionInput>>(this.base + '/saved-positions'); }
  savePosition(symbol: string, input: StockPositionInput) { return this.http.put<void>(this.base + '/saved-positions/' + symbol, { ...input, purchaseDate: input.purchaseDate || null }); }
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
