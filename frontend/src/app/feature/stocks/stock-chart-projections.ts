import { StockCandle } from '../../core/models/stock.model';
export const FAN_RATIOS = [0.382, 0.5, 0.618] as const;
export interface ProjectionAnchor {
  index: number;
  time: number;
  price: number;
}
export interface StockChartProjection {
  lastIndex: number;
  lastTime: number;
  lastClose: number;
  atr: number;
  window: number;
  start: ProjectionAnchor;
  end: ProjectionAnchor;
  anchorMethod: 'pivots' | 'extremes';
  direction: 'UP' | 'DOWN';
  retracements: { ratio: number; price: number; slope: number }[];
  regression: { startIndex: number; intercept: number; slope: number; deviation: number };
}
/** Display geometry only. Input is the chart's canonical candles; indices are actual bar positions, not future dates. */
export function stockChartProjection(
  candles: StockCandle[],
  window = 60,
): StockChartProjection | null {
  if (![30, 60, 120].includes(window)) throw new Error('Unsupported projection window');
  const closed = candles.map((bar, index) => ({ bar, index })).filter((p) => p.bar.closed);
  if (closed.length < 20) return null;
  const points = closed.slice(-window),
    last = points.at(-1)!;
  let tr = 0;
  for (let i = closed.length - 14; i < closed.length; i++) {
    const bar = closed[i].bar,
      previous = closed[i - 1].bar.close;
    tr += Math.max(bar.high - bar.low, Math.abs(bar.high - previous), Math.abs(bar.low - previous));
  }
  const atr = tr / 14;
  if (!Number.isFinite(atr) || atr <= 0) return null;
  const anchor = (p: typeof last, high: boolean): ProjectionAnchor => ({
    index: p.index,
    time: p.bar.time,
    price: high ? p.bar.high : p.bar.low,
  });
  const pivots: { high: boolean; anchor: ProjectionAnchor }[] = [];
  for (let i = 2; i < points.length - 2; i++) {
    const p = points[i],
      neighbors = [points[i - 2], points[i - 1], points[i + 1], points[i + 2]];
    const high =
      neighbors.every((n) => p.bar.high >= n.bar.high) &&
      neighbors.some((n) => p.bar.high > n.bar.high);
    const low =
      neighbors.every((n) => p.bar.low <= n.bar.low) &&
      neighbors.some((n) => p.bar.low < n.bar.low);
    if (high !== low) pivots.push({ high, anchor: anchor(p, high) });
  }
  let start: ProjectionAnchor | undefined,
    end: ProjectionAnchor | undefined,
    anchorMethod: 'pivots' | 'extremes' = 'pivots';
  for (let i = pivots.length - 1; i > 0 && !start; i--) {
    const b = pivots[i];
    for (let j = i - 1; j >= 0; j--) {
      const a = pivots[j];
      if (
        a.high !== b.high &&
        b.anchor.index - a.anchor.index >= 2 &&
        Math.abs(b.anchor.price - a.anchor.price) >= atr
      ) {
        start = a.anchor;
        end = b.anchor;
        break;
      }
    }
  }
  if (!start || !end) {
    anchorMethod = 'extremes';
    const low = points.reduce((a, b) => (b.bar.low < a.bar.low ? b : a)),
      high = points.reduce((a, b) => (b.bar.high > a.bar.high ? b : a));
    if (low.index === high.index || high.bar.high - low.bar.low < atr) return null;
    [start, end] =
      low.index < high.index
        ? [anchor(low, false), anchor(high, true)]
        : [anchor(high, true), anchor(low, false)];
  }
  const distance = end.index - start.index,
    move = end.price - start.price;
  if (distance < 1 || Math.abs(move) < atr) return null;
  const meanIndex = points.reduce((sum, p) => sum + p.index, 0) / points.length;
  const meanPrice = points.reduce((sum, p) => sum + p.bar.close, 0) / points.length;
  const divisor = points.reduce((sum, p) => sum + (p.index - meanIndex) ** 2, 0);
  const slope =
    points.reduce((sum, p) => sum + (p.index - meanIndex) * (p.bar.close - meanPrice), 0) / divisor;
  const intercept = meanPrice - slope * meanIndex;
  const deviation = Math.sqrt(
    points.reduce((sum, p) => sum + (p.bar.close - intercept - slope * p.index) ** 2, 0) /
      points.length,
  );
  return {
    lastIndex: last.index,
    lastTime: last.bar.time,
    lastClose: last.bar.close,
    atr,
    window: points.length,
    start,
    end,
    anchorMethod,
    direction: move > 0 ? 'UP' : 'DOWN',
    retracements: FAN_RATIOS.map((ratio) => ({
      ratio,
      price: end.price - move * ratio,
      slope: (move * (1 - ratio)) / distance,
    })),
    regression: { startIndex: points[0].index, intercept, slope, deviation },
  };
}
export function fanPriceAt(model: StockChartProjection, ratio: number, index: number): number {
  const line = model.retracements.find((line) => line.ratio === ratio);
  if (!line) throw new Error('Unknown fan ratio');
  return model.start.price + line.slope * (index - model.start.index);
}
