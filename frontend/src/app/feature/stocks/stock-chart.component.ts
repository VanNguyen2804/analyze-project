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
} from 'lightweight-charts';
import { StockCandle } from '../../core/models/stock.model';

@Component({
  selector: 'app-stock-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template:
    '<div #host class="chart" role="img" aria-label="Biểu đồ nến, khối lượng và đường trung bình EMA20; có thể kéo và thu phóng"></div>@if (renderError()) {<p role="alert">{{ renderError() }}</p>}<p class="credit">TradingView Lightweight Charts™ · Copyright (с) 2025 TradingView, Inc. <a href="https://www.tradingview.com/" target="_blank" rel="noopener noreferrer">TradingView</a></p>',
  styles: [
    ':host{display:block;width:100%;min-width:0}.chart{height:430px;width:100%;min-width:0}.credit{font-size:11px;color:#64748b;margin:8px 0}@media(max-width:600px){.chart{height:330px}}',
  ],
})
export class StockChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('host') host!: ElementRef<HTMLDivElement>;
  @Input() candles: StockCandle[] = [];
  @Input() intraday = false;
  @Input() purchasePrice: number | null = null;
  @Input() purchaseDate = '';
  private chart?: IChartApi;
  private candlesSeries?: ISeriesApi<'Candlestick'>;
  private volumeSeries?: ISeriesApi<'Histogram'>;
  private emaSeries?: ISeriesApi<'Line'>;
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
      layout: { textColor: '#64748b', attributionLogo: true },
      grid: { vertLines: { color: '#f1f5f9' }, horzLines: { color: '#f1f5f9' } },
      localization: { locale: 'vi-VN' },
    });
    this.candlesSeries = this.chart.addSeries(CandlestickSeries, {
      upColor: '#059669',
      downColor: '#e11d48',
      borderVisible: false,
      wickUpColor: '#059669',
      wickDownColor: '#e11d48',
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
    this.markers = createSeriesMarkers(this.candlesSeries);
    this.render();
  }
  ngOnChanges() {
    this.render();
  }
  private render() {
    if (!this.chart) return;
    try {
      this.renderError.set('');
      const formatter = new Intl.DateTimeFormat('en-US', {
        timeZone: 'Asia/Ho_Chi_Minh',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      });
      const date = (timestamp: number) => {
        const parts = formatter.formatToParts(new Date(timestamp * 1000));
        const part = (type: string) => parts.find((p) => p.type === type)!.value;
        return `${part('year')}-${part('month')}-${part('day')}`;
      };
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
        this.candles.map((c) => ({
          time: time(c),
          open: c.open,
          high: c.high,
          low: c.low,
          close: c.close,
          ...(c.closed ? {} : { color: '#94a3b8', wickColor: '#94a3b8' }),
        })),
      );
      this.volumeSeries!.setData(
        this.candles.map((c) => ({
          time: time(c),
          value: c.volume,
          color: c.close >= c.open ? '#a7f3d0' : '#fecdd3',
        })),
      );
      let ema = this.candles[0]?.close ?? 0;
      this.emaSeries!.setData(
        this.candles.map((c) => {
          ema += (2 / 21) * (c.close - ema);
          return { time: time(c), value: ema };
        }),
      );
      if (this.purchaseLine) {
        this.candlesSeries!.removePriceLine(this.purchaseLine);
        this.purchaseLine = undefined;
      }
      if (
        this.purchasePrice !== null &&
        Number.isFinite(this.purchasePrice) &&
        this.purchasePrice > 0
      ) {
        this.purchaseLine = this.candlesSeries!.createPriceLine({
          price: this.purchasePrice,
          color: '#d97706',
          lineWidth: 2,
          title: 'Giá mua',
          axisLabelVisible: true,
        });
      }
      const purchaseBar = this.candles.find((c) => date(c.time) === this.purchaseDate);
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
      if (this.candles.length)
        this.chart.timeScale().setVisibleLogicalRange({
          from: Math.max(0, this.candles.length - 100),
          to: this.candles.length + 3,
        });
    } catch {
      this.renderError.set('Không vẽ được biểu đồ từ dữ liệu hiện tại. Hãy cập nhật lại dữ liệu.');
    }
  }
  ngOnDestroy() {
    this.destroyed = true;
    cancelAnimationFrame(this.resizeFrame);
    this.observer?.disconnect();
    this.markers?.detach();
    this.chart?.remove();
  }
}
