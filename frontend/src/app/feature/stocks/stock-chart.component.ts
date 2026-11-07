import { DatePipe, DecimalPipe } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  ViewChild,
  signal,
} from '@angular/core';
import {
  CandlestickSeries,
  HistogramSeries,
  LineSeries,
  createChart,
  IChartApi,
  ISeriesApi,
  UTCTimestamp,
  IPriceLine,
  ISeriesMarkersPluginApi,
  Time,
  createSeriesMarkers,
  ColorType,
  AutoscaleInfo,
} from 'lightweight-charts';
import { StockCandle, StockPlanLevel } from '../../core/models/stock.model';
import { stockBusinessDate, stockChartCandles } from './stock-chart-data';
import { StockCloud } from './stock-cloud';
import { StockProjectionLayer } from './stock-projection-layer';
import { stockChartProjection, StockChartProjection } from './stock-chart-projections';

@Component({
  selector: 'app-stock-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './stock-chart.component.html',
  styleUrl: './stock-chart.component.css',
})
export class StockChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('host') host!: ElementRef<HTMLDivElement>;
  @Input() candles: StockCandle[] = [];
  @Input() intraday = false;
  @Input() purchasePrice: number | null = null;
  @Input() purchaseDate = '';
  @Input() planLevels: StockPlanLevel[] = [];
  readonly planVisible = signal(true);
  readonly projection = signal<StockChartProjection | null>(null);
  readonly fanVisible = signal(true);
  readonly fibVisible = signal(false);
  readonly channelVisible = signal(false);
  readonly projectionWindow = signal(60);
  readonly projectionHorizon = 20;
  private projectionLayer?: StockProjectionLayer;
  toggleProjection(layer: 'fan' | 'levels' | 'channel') {
    const state =
      layer === 'fan'
        ? this.fanVisible
        : layer === 'levels'
          ? this.fibVisible
          : this.channelVisible;
    state.update((value) => !value);
    this.updateProjection();
  }
  setProjectionWindow(value: string) {
    const window = Number(value);
    if (![30, 60, 120].includes(window)) return;
    this.projectionWindow.set(window);
    this.render();
  }
  private updateProjection() {
    this.projectionLayer?.setData(this.projection(), {
      fan: this.fanVisible(),
      levels: this.fibVisible(),
      channel: this.channelVisible(),
      horizon: this.projectionHorizon,
    });
  }

  private planLines: IPriceLine[] = [];
  private chart?: IChartApi;
  private candlesSeries?: ISeriesApi<'Candlestick'>;
  private volumeSeries?: ISeriesApi<'Histogram'>;
  private emaSeries?: ISeriesApi<'Line'>;
  private ema50Series?: ISeriesApi<'Line'>;
  private cloud?: StockCloud;
  private cloudASeries?: ISeriesApi<'Line'>;
  private cloudBSeries?: ISeriesApi<'Line'>;
  readonly cloudVisible = signal(true);
  private visibleBars = 90;
  private candleCount = 0;
  private purchaseLine?: IPriceLine;
  private markers?: ISeriesMarkersPluginApi<Time>;
  private observer?: ResizeObserver;
  private resizeFrame = 0;
  private destroyed = false;
  readonly renderError = signal('');
  ngAfterViewInit() {
    this.observer = new ResizeObserver(() => {
      cancelAnimationFrame(this.resizeFrame);
      this.resizeFrame = requestAnimationFrame(() => {
        this.resizeFrame = 0;
        this.resize();
      });
    });
    this.observer.observe(this.host.nativeElement);
    this.resize();
  }
  private resize() {
    if (this.destroyed) return;
    const host = this.host.nativeElement;
    if (host.clientWidth < 1 || host.clientHeight < 1) return;
    if (!this.chart) this.initialize(host.clientWidth, host.clientHeight);
    else this.chart.resize(host.clientWidth, host.clientHeight);
  }
  private initialize(width: number, height: number) {
    this.chart = createChart(this.host.nativeElement, {
      autoSize: false,
      width,
      height,
      layout: {
        textColor: '#a6b6ce',
        background: { type: ColorType.Solid, color: '#111e33' },
        attributionLogo: true,
      },
      grid: { vertLines: { color: '#1e2e46' }, horzLines: { color: '#1e2e46' } },
      localization: {
        locale: 'vi-VN',
        priceFormatter: (value: number) =>
          new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value),
      },
    });
    this.candlesSeries = this.chart.addSeries(CandlestickSeries, {
      upColor: '#059669',
      downColor: '#e11d48',
      borderVisible: false,
      wickUpColor: '#059669',
      wickDownColor: '#e11d48',
      autoscaleInfoProvider: (base: () => AutoscaleInfo | null) => {
        const info = base();
        if (!info?.priceRange || !this.planVisible()) return info;
        const levels = this.planLevels
          .map((l) => l.price)
          .filter((p) => Number.isFinite(p) && p > 0);
        if (!levels.length) return info;
        return {
          ...info,
          priceRange: {
            minValue: Math.min(info.priceRange.minValue, ...levels),
            maxValue: Math.max(info.priceRange.maxValue, ...levels),
          },
        };
      },
    });
    this.volumeSeries = this.chart.addSeries(HistogramSeries, {
      priceFormat: { type: 'volume' },
      priceScaleId: 'volume',
    });
    this.chart.priceScale('volume').applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });
    this.chart.priceScale('right').applyOptions({ scaleMargins: { top: 0.08, bottom: 0.22 } });
    this.emaSeries = this.chart.addSeries(LineSeries, {
      color: '#6366f1',
      lineWidth: 2,
      title: 'EMA20',
      priceLineVisible: false,
    });
    this.ema50Series = this.chart.addSeries(LineSeries, {
      color: '#d97706',
      lineWidth: 1,
      title: 'EMA50',
      priceLineVisible: false,
      lastValueVisible: false,
    });
    this.cloud = new StockCloud(this.chart, this.candlesSeries);
    this.candlesSeries.attachPrimitive(this.cloud);
    this.cloudASeries = this.chart.addSeries(LineSeries, {
      color: 'rgba(5,150,105,0.35)',
      lineWidth: 1,
      lastValueVisible: false,
      priceLineVisible: false,
    });
    this.cloudBSeries = this.chart.addSeries(LineSeries, {
      color: 'rgba(225,29,72,0.3)',
      lineWidth: 1,
      lastValueVisible: false,
      priceLineVisible: false,
    });
    this.projectionLayer = new StockProjectionLayer(this.chart, this.candlesSeries);
    this.candlesSeries.attachPrimitive(this.projectionLayer);
    this.markers = createSeriesMarkers(this.candlesSeries);
    this.render();
  }
  ngOnChanges() {
    this.render();
  }
  setRange(bars: number) {
    this.visibleBars = bars;
    this.applyRange();
  }
  toggleCloud() {
    this.cloudVisible.update((v) => !v);
    this.render();
  }
  togglePlan() {
    this.planVisible.update((v) => !v);
    this.render();
  }
  private applyRange() {
    if (this.chart && this.candleCount)
      this.chart.timeScale().setVisibleLogicalRange({
        from: Math.max(0, this.candleCount - this.visibleBars),
        to:
          this.candleCount +
          (this.projection() && (this.fanVisible() || this.fibVisible() || this.channelVisible())
            ? this.projectionHorizon
            : 3),
      });
  }
  private render() {
    if (!this.chart) return;
    try {
      this.renderError.set('');
      const candles = stockChartCandles(this.candles, this.intraday);
      const date = stockBusinessDate;
      const time = (c: StockCandle) => (this.intraday ? (c.time as UTCTimestamp) : date(c.time));
      this.chart.applyOptions({
        timeScale: {
          timeVisible: this.intraday,
          secondsVisible: false,
          tickMarkFormatter: (t: Time) =>
            this.intraday && typeof t === 'number'
              ? new Date(t * 1000).toLocaleTimeString('vi-VN', {
                  timeZone: 'Asia/Ho_Chi_Minh',
                  hour: '2-digit',
                  minute: '2-digit',
                })
              : null,
        },
        localization: {
          timeFormatter: (t: number | object | string) =>
            typeof t === 'number'
              ? new Date(t * 1000).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
              : typeof t === 'object' && 'year' in t && 'month' in t && 'day' in t
                ? `${t.day}/${t.month}/${t.year}`
                : String(t),
        },
      });
      this.candlesSeries!.setData(
        candles.map((c) => ({
          time: time(c),
          open: c.open,
          high: c.high,
          low: c.low,
          close: c.close,
          ...(c.closed ? {} : { color: '#94a3b8', wickColor: '#94a3b8' }),
        })),
      );
      this.volumeSeries!.setData(
        candles.map((c) => ({
          time: time(c),
          value: c.volume,
          color: c.close >= c.open ? '#a7f3d0' : '#fecdd3',
        })),
      );
      let ema = candles[0]?.close ?? 0;
      this.emaSeries!.setData(
        candles.map((c) => {
          ema += (2 / 21) * (c.close - ema);
          return { time: time(c), value: ema };
        }),
      );
      let ema50 = candles[0]?.close ?? 0;
      this.ema50Series!.setData(
        candles.map((c) => {
          ema50 += (2 / 51) * (c.close - ema50);
          return { time: time(c), value: ema50 };
        }),
      );
      const midpoint = (end: number, period: number) => {
        const window = candles.slice(end - period + 1, end + 1);
        return (Math.max(...window.map((c) => c.high)) + Math.min(...window.map((c) => c.low))) / 2;
      };
      const cloudPoints = [];
      if (this.cloudVisible())
        for (let i = 77; i < candles.length; i++) {
          const origin = i - 26;
          cloudPoints.push({
            time: time(candles[i]),
            a: (midpoint(origin, 9) + midpoint(origin, 26)) / 2,
            b: midpoint(origin, 52),
          });
        }
      this.cloud!.setData(cloudPoints);
      this.cloudASeries!.setData(cloudPoints.map((p) => ({ time: p.time, value: p.a })));
      this.cloudBSeries!.setData(cloudPoints.map((p) => ({ time: p.time, value: p.b })));
      if (this.purchaseLine) {
        this.candlesSeries!.removePriceLine(this.purchaseLine);
        this.purchaseLine = undefined;
      }
      for (const line of this.planLines) this.candlesSeries!.removePriceLine(line);
      this.planLines = [];
      if (this.planVisible())
        for (const level of this.planLevels) {
          if (Number.isFinite(level.price) && level.price > 0)
            this.planLines.push(
              this.candlesSeries!.createPriceLine({
                price: level.price,
                color: level.color,
                lineWidth: 1,
                title: level.title,
                axisLabelVisible: true,
              }),
            );
        }
      if (
        this.planVisible() &&
        !this.planLevels.some((l) => l.title === 'Giá vốn') &&
        this.purchasePrice !== null &&
        Number.isFinite(this.purchasePrice) &&
        this.purchasePrice > 0
      ) {
        this.purchaseLine = this.candlesSeries!.createPriceLine({
          price: this.purchasePrice,
          color: '#d97706',
          lineWidth: 2,
          title: 'Giá vốn',
          axisLabelVisible: true,
        });
      }
      const purchaseBar = candles.find((c) => date(c.time) === this.purchaseDate);
      this.markers?.setMarkers(
        purchaseBar && this.purchasePrice !== null
          ? [
              {
                time: time(purchaseBar),
                position: 'belowBar',
                shape: 'arrowUp',
                color: '#d97706',
                text: this.intraday ? 'Ngày mua (chưa biết giờ)' : 'Ngày mua',
              },
            ]
          : [],
      );
      this.projection.set(stockChartProjection(candles, this.projectionWindow()));
      this.updateProjection();
      this.candleCount = candles.length;
      this.applyRange();
    } catch {
      this.renderError.set('Không vẽ được biểu đồ từ dữ liệu hiện tại. Hãy cập nhật lại dữ liệu.');
    }
  }
  ngOnDestroy() {
    this.destroyed = true;
    cancelAnimationFrame(this.resizeFrame);
    this.observer?.disconnect();
    this.markers?.detach();
    if (this.cloud) this.candlesSeries?.detachPrimitive(this.cloud);
    if (this.projectionLayer) this.candlesSeries?.detachPrimitive(this.projectionLayer);
    this.chart?.remove();
  }
}
