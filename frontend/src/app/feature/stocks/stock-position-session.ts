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
/** Persist inputs locally; recalculate results from current market data. */
@Injectable({ providedIn: 'root' })
export class StockPositionSessions {
  private readonly entries = new Map<string, StockPositionSession>();
  private restore(symbol: string): StockPositionInput {
    try {
      const saved = JSON.parse(localStorage.getItem('stocks.positions.v1') || '{}')[symbol];
      if (saved && Number.isFinite(saved.purchasePrice) && saved.purchasePrice > 0 && Number.isInteger(saved.quantity) && saved.quantity > 0) return { ...newStockPosition(), ...saved };
    } catch {}
    return newStockPosition();
  }
  save() {
    try { localStorage.setItem('stocks.positions.v1', JSON.stringify(Object.fromEntries([...this.entries].map(([k, v]) => [k, v.input])))); } catch {}
  }
  forSymbol(symbol: string): StockPositionSession {
    if (!this.entries.has(symbol))
      this.entries.set(symbol, { input: this.restore(symbol), result: null, autoAnalyze: false });
    return this.entries.get(symbol)!;
  }
}
