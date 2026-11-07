import {
  IChartApi,
  ISeriesApi,
  ISeriesPrimitive,
  IPrimitivePaneView,
  Logical,
  Time,
  SeriesAttachedParameter,
  AutoscaleInfo,
} from 'lightweight-charts';
import { CanvasRenderingTarget2D } from 'fancy-canvas';
import { StockChartProjection, fanPriceAt } from './stock-chart-projections';
export interface ProjectionLayers {
  fan: boolean;
  levels: boolean;
  channel: boolean;
  horizon: number;
}
export class StockProjectionLayer implements ISeriesPrimitive<Time> {
  private model: StockChartProjection | null = null;
  private layers: ProjectionLayers = { fan: true, levels: false, channel: false, horizon: 20 };
  private requestUpdate?: () => void;
  private readonly views: IPrimitivePaneView[] = [
    { zOrder: () => 'normal', renderer: () => ({ draw: (target) => this.draw(target) }) },
  ];
  constructor(
    private chart: IChartApi,
    private series: ISeriesApi<'Candlestick'>,
  ) {}
  attached(parameters: SeriesAttachedParameter<Time>) {
    this.requestUpdate = parameters.requestUpdate;
  }
  detached() {
    this.requestUpdate = undefined;
  }
  setData(model: StockChartProjection | null, layers: ProjectionLayers) {
    this.model = model;
    this.layers = layers;
    this.requestUpdate?.();
  }
  paneViews() {
    return this.views;
  }
  autoscaleInfo(start: Logical, end: Logical): AutoscaleInfo | null {
    const m = this.model,
      l = this.layers;
    if (
      !m ||
      (!l.fan && !l.levels && !l.channel) ||
      end < m.start.index ||
      start > m.lastIndex + l.horizon
    )
      return null;
    // Bounded padding: steep extrapolations must never flatten the real candles.
    return {
      priceRange: {
        minValue: Math.max(m.lastClose * 0.01, m.lastClose - 3 * m.atr),
        maxValue: m.lastClose + 3 * m.atr,
      },
    };
  }
  private draw(target: CanvasRenderingTarget2D) {
    const m = this.model,
      l = this.layers;
    if (!m || (!l.fan && !l.levels && !l.channel)) return;
    target.useMediaCoordinateSpace(({ context: ctx, mediaSize }) => {
      const x = (index: number) => {
        // v5.2 accepts integer logical indices only; interpolate between bars for the boundary.
        const lower = Math.floor(index),
          left = this.chart.timeScale().logicalToCoordinate(lower as Logical);
        if (left === null || index === lower) return left;
        const right = this.chart.timeScale().logicalToCoordinate((lower + 1) as Logical);
        return right === null ? null : left + (right - left) * (index - lower);
      };
      const y = (price: number) => this.series.priceToCoordinate(price);
      const end = m.lastIndex + l.horizon,
        edge = x(end),
        boundary = x(m.lastIndex + 0.5);
      if (edge === null || boundary === null) return;
      ctx.save();
      ctx.beginPath();
      ctx.rect(0, 0, mediaSize.width, mediaSize.height);
      ctx.clip();
      if (boundary < mediaSize.width) {
        ctx.fillStyle = '#8b5cf610';
        ctx.fillRect(
          Math.max(0, boundary),
          0,
          mediaSize.width - Math.max(0, boundary),
          mediaSize.height,
        );
        ctx.strokeStyle = '#a78bfa70';
        ctx.setLineDash([4, 5]);
        ctx.beginPath();
        ctx.moveTo(boundary, 0);
        ctx.lineTo(boundary, mediaSize.height);
        ctx.stroke();
        ctx.fillStyle = '#c4b5fd';
        ctx.font = '10px sans-serif';
        ctx.fillText('KỊCH BẢN', Math.max(6, boundary + 6), 18);
      }
      const line = (
        from: number,
        p1: number,
        to: number,
        p2: number,
        color: string,
        label: string,
        dashed = true,
      ) => {
        const a = x(from),
          b = x(to),
          c = y(p1),
          d = y(p2);
        if (
          a === null ||
          b === null ||
          c === null ||
          d === null ||
          !Number.isFinite(c) ||
          !Number.isFinite(d)
        )
          return;
        ctx.strokeStyle = color;
        ctx.lineWidth = 1.3;
        ctx.setLineDash(dashed ? [5, 4] : []);
        ctx.beginPath();
        ctx.moveTo(a, c);
        ctx.lineTo(b, d);
        ctx.stroke();
        if (d > 34 && d < mediaSize.height * 0.77) {
          ctx.font = '10px sans-serif';
          ctx.fillStyle = color;
          const width = ctx.measureText(label).width;
          ctx.fillText(
            label,
            Math.max(4, Math.min(b - width - 5, mediaSize.width - width - 5)),
            d - 5,
          );
        }
      };
      if (l.fan) {
        line(m.start.index, m.start.price, m.end.index, m.end.price, '#94a3b8', '', false);
        m.retracements.forEach((r, i) =>
          line(
            m.start.index,
            m.start.price,
            end,
            fanPriceAt(m, r.ratio, end),
            ['#c4b5fd', '#67e8f9', '#f9a8d4'][i],
            `Fan ${(r.ratio * 100).toFixed(1)}%`,
          ),
        );
        for (const [name, p] of [
          ['A', m.start],
          ['B', m.end],
        ] as const) {
          const a = x(p.index),
            b = y(p.price);
          if (a !== null && b !== null) {
            ctx.setLineDash([]);
            ctx.fillStyle = '#e2e8f0';
            ctx.beginPath();
            ctx.arc(a, b, 3, 0, Math.PI * 2);
            ctx.fill();
            ctx.font = 'bold 11px sans-serif';
            ctx.fillText(name, a + 5, b - 7);
          }
        }
      }
      if (l.levels)
        m.retracements.forEach((r) =>
          line(m.end.index, r.price, end, r.price, '#fbbf24', `Fib ${(r.ratio * 100).toFixed(1)}%`),
        );
      if (l.channel) {
        const r = m.regression;
        for (const offset of [-2, 0, 2])
          line(
            r.startIndex,
            r.intercept + r.slope * r.startIndex + offset * r.deviation,
            end,
            r.intercept + r.slope * end + offset * r.deviation,
            offset === 0 ? '#38bdf8' : '#38bdf870',
            offset === 0 ? 'Xu hướng ngoại suy' : '',
          );
      }
      ctx.restore();
    });
  }
}
