import { Injectable } from '@angular/core';
import { StockPositionInput, StockPositionResult } from '../../core/models/stock.model';

export function newStockPosition(): StockPositionInput {
  return {
    purchaseDate: '',
    purchasePrice: null,
    quantity: null,
    maxLossPercent: null,
    sellableQuantity: null,
  };
}

export interface StockPositionSession {
  input: StockPositionInput;
  result: StockPositionResult | null;
  autoAnalyze: boolean;
}
/** In-memory only, scoped to this browser session; never written to storage. */
@Injectable({ providedIn: 'root' })
export class StockPositionSessions {
  private readonly entries = new Map<string, StockPositionSession>();
  forSymbol(symbol: string): StockPositionSession {
    if (!this.entries.has(symbol))
      this.entries.set(symbol, { input: newStockPosition(), result: null, autoAnalyze: false });
    return this.entries.get(symbol)!;
  }
}
