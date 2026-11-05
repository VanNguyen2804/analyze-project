import { Injectable } from '@angular/core';
import { StockPositionInput, StockPositionResult } from '../../core/models/stock.model';

export function newStockPosition(): StockPositionInput {
  return { purchaseDate: '', purchasePrice: null, quantity: null, maxLossPercent: 10, sellableQuantity: null,
    plan: { startedAt: new Date().toISOString(), observedPeak: null, availableQuantity: null, executions: [],
      policy: { alertLossPercent: 5, maxLossVnd: null, additionalBudget: 0, maxCapital: null,
        averagingBudgets: [0, 0], targets: [
          { gainPercent: 10, price: null, sellPercent: 30 }, { gainPercent: 20, price: null, sellPercent: 30 }], trailingPercent: null } } };
}
export interface StockPositionSession { input: StockPositionInput; result: StockPositionResult | null; autoAnalyze: boolean; }
/** In-memory only, scoped to this browser session; never written to storage. */
@Injectable({ providedIn: 'root' })
export class StockPositionSessions {
  private readonly entries = new Map<string, StockPositionSession>();
  forSymbol(symbol: string): StockPositionSession {
    if (!this.entries.has(symbol)) this.entries.set(symbol, { input: newStockPosition(), result: null, autoAnalyze: false });
    return this.entries.get(symbol)!;
  }
}
