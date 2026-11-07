import { stockChartProjection, fanPriceAt, FAN_RATIOS } from './stock-chart-projections';
import { StockCandle } from '../../core/models/stock.model';
const bars = (count = 60, direction = 1): StockCandle[] =>
  Array.from({ length: count }, (_, i) => {
    const close = direction === 1 ? 100 + i * 2 : 220 - i * 2;
    return {
      time: 1700000000 + i * 86400,
      open: close - 0.2,
      high: close + 1,
      low: close - 1,
      close,
      volume: 1000,
      closed: true,
    };
  });
describe('Automatic chart projection geometry', () => {
  it('places every fan through A and its matching Fibonacci retracement at B in either direction', () => {
    for (const direction of [1, -1]) {
      const model = stockChartProjection(bars(60, direction))!;
      expect(model.direction).toBe(direction === 1 ? 'UP' : 'DOWN');
      expect(model.start.index).toBeLessThan(model.end.index);
      for (const ratio of FAN_RATIOS) {
        expect(fanPriceAt(model, ratio, model.start.index)).toBeCloseTo(model.start.price, 10);
        const retracement = model.retracements.find((r) => r.ratio === ratio)!;
        expect(fanPriceAt(model, ratio, model.end.index)).toBeCloseTo(retracement.price, 10);
        expect(retracement.price).toBeCloseTo(
          model.end.price - (model.end.price - model.start.price) * ratio,
          10,
        );
      }
    }
  });
  it('ignores unfinished bars and uses only the selected closed history window', () => {
    const input = bars(),
      expected = stockChartProjection(input);
    const future = {
      ...input.at(-1)!,
      time: input.at(-1)!.time + 86400,
      high: 99999,
      low: 1,
      close: 90000,
      closed: false,
    };
    expect(stockChartProjection([...input, future])).toEqual(expected);
    const changed = [
      ...bars(80),
      ...bars(60).map((b, i) => ({ ...b, time: 1700000000 + (i + 80) * 86400 })),
    ];
    const model = stockChartProjection(changed, 30)!;
    expect(model.window).toBe(30);
    expect(model.start.index).toBeGreaterThanOrEqual(110);
  });
  it('uses confirmed pivots when available, with two existing closed bars to their right', () => {
    const input = bars(80).map((b, i) => {
      const close = 100 + Math.sin(i / 4) * 12;
      return { ...b, open: close - 0.2, high: close + 1, low: close - 1, close };
    });
    const model = stockChartProjection(input)!;
    expect(model.anchorMethod).toBe('pivots');
    expect(model.end.index).toBeLessThanOrEqual(input.length - 3);
    expect(model.start.index).toBeLessThan(model.end.index);
  });
  it('does not invent a swing from insufficient or flat data and bounds the settings', () => {
    expect(stockChartProjection(bars(10))).toBeNull();
    expect(
      stockChartProjection(
        bars().map((b) => ({ ...b, open: 100, close: 100, high: 100, low: 100 })),
      ),
    ).toBeNull();
    expect(() => stockChartProjection(bars(), 10000)).toThrow();
  });
  it('fits the close-price trend rather than labeling a confidence interval', () => {
    const input = bars(),
      model = stockChartProjection(input)!;
    expect(model.regression.slope).toBeCloseTo(2, 10);
    expect(model.regression.intercept).toBeCloseTo(100, 10);
    expect(model.regression.deviation).toBeCloseTo(0, 10);
    expect(input.length).toBe(60);
    expect(model.lastIndex).toBe(59);
  });
});
