import { StockCandle } from '../../core/models/stock.model';

const dayFormatter = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Ho_Chi_Minh',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
export function stockBusinessDate(timestamp: number): string {
  const parts = dayFormatter.formatToParts(new Date(timestamp * 1000));
  const part = (type: string) => parts.find((p) => p.type === type)!.value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}
/** Defensive compatibility with older servers: one candle per chart time, latest timestamp wins. */
export function stockChartCandles(input: StockCandle[], intraday: boolean): StockCandle[] {
  const result = new Map<string | number, StockCandle>();
  for (const candle of [...input].sort((a, b) => a.time - b.time))
    result.set(intraday ? candle.time : stockBusinessDate(candle.time), candle);
  return [...result.values()];
}
