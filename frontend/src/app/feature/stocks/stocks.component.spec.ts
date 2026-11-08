import { BackendReadinessService } from '../../core/services/backend-readiness.service';
import { of as readyNow } from 'rxjs';
import { Component } from '@angular/core';
import { By } from '@angular/platform-browser';
import {
  DeferBlockBehavior,
  DeferBlockState,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { StocksComponent } from './stocks.component';
import { HeaderComponent } from '../../share/components/header/header.component';
import { SectionService } from '../../core/services/section.service';
import { CategoryService } from '../../core/services/category.service';
import { StockSnapshot } from '../../core/models/stock.model';
import { StockChartComponent } from './stock-chart.component';

@Component({ template: '' })
class EmptyComponent {}
const emptySnapshot = (symbol: string): StockSnapshot => ({
  symbol,
  mode: 'SWING',
  interval: '1D',
  snapshotId: symbol + '-snapshot',
  freshness: 'EMPTY',
  marketStatus: 'UNVERIFIED_CALENDAR',
  lastClosedBarAt: null,
  data: {
    candles: [],
    fetchedAt: '2026-11-03T09:30:00Z',
    source: 'TEST',
    unit: 'SOURCE',
    priceBasis: 'UNKNOWN',
    verified: false,
    warnings: [],
  },
  benchmark: {
    candles: [],
    fetchedAt: '2026-11-03T09:30:00Z',
    source: 'TEST',
    unit: 'POINTS',
    priceBasis: 'UNKNOWN',
    verified: false,
    warnings: [],
  },
  analysis: {
    signal: 'UNAVAILABLE',
    reasons: [],
    indicators: null,
    learning: {
      version: 'test',
      status: 'INSUFFICIENT_DATA',
      signal: 'UNAVAILABLE',
      horizonBars: 10,
      trainSamples: 0,
      testSamples: 0,
      accuracy: null,
      balancedAccuracy: null,
      baselineAccuracy: null,
      logLoss: null,
      buyScore: null,
      holdScore: null,
      sellScore: null,
      trainingLabelsThrough: null,
      weights: [],
      explanation: 'Thiếu dữ liệu',
    },
  },
});

describe('Stocks page', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        {
          provide: BackendReadinessService,
          useValue: {
            ready: () => readyNow(undefined),
            waiting: () => false,
            invalidate: () => {},
          },
        },
      ],
    }),
  );
  it('creates the chart only after deferred activation and passes the latest symbol data', async () => {
    TestBed.configureTestingModule({ deferBlockBehavior: DeferBlockBehavior.Manual });
    const fixture = TestBed.createComponent(StocksComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    const snapshot = emptySnapshot('FPT');
    snapshot.data.candles = [
      { time: 1700000000, open: 10, high: 12, low: 9, close: 11, volume: 100, closed: true },
    ];
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(snapshot);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-stock-chart')).toBeNull();
    expect(fixture.nativeElement.querySelector('.deferred-chart')).not.toBeNull();
    const [chart] = await fixture.getDeferBlocks();
    fixture.componentInstance.setMode('INTRADAY');
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush({ ...snapshot, mode: 'INTRADAY' });
    fixture.detectChanges();
    await chart.render(DeferBlockState.Complete);
    expect(fixture.nativeElement.querySelector('app-stock-chart')).not.toBeNull();
    const rendered = fixture.debugElement.query(By.directive(StockChartComponent))
      .componentInstance as StockChartComponent;
    expect(rendered.intraday).toBeTrue();
    expect(rendered.candles).toEqual(snapshot.data.candles);
    expect(fixture.nativeElement.querySelector('.deferred-chart')).toBeNull();
    fixture.destroy();
    http.verify();
  });
  it('posts only the two position inputs without assigning a budget or sellable shares', () => {
    const fixture = TestBed.createComponent(StocksComponent),
      http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(emptySnapshot('FPT'));
    fixture.componentInstance.input.purchasePrice = 25000;
    fixture.componentInstance.input.quantity = 100;
    fixture.componentInstance.analyzePosition();
    const request = http.expectOne((r) => r.url.endsWith('/FPT/position-analysis'));
    expect(request.request.body).toEqual({ purchasePrice: 25000, quantity: 100 });
    expect(request.request.params.get('snapshotId')).toBe('FPT-snapshot');
    request.flush({
      snapshotId: 'FPT-snapshot',
      status: 'WATCH',
      price: null,
      changePercent: null,
      grossPnl: null,
      estimatedNetPnl: null,
      breakEven: null,
      reasons: [],
    });
    fixture.destroy();
    http.verify();
  });
  it('refreshes only the selected analysis each minute and the watchlist every five minutes', fakeAsync(() => {
    spyOnProperty(document, 'hidden', 'get').and.returnValue(false);
    const fixture = TestBed.createComponent(StocksComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(emptySnapshot('FPT'));
    for (let minute = 1; minute <= 5; minute++) {
      tick(60000);
      http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(emptySnapshot('FPT'));
      if (minute === 5) http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
      else http.expectNone((r) => r.url.endsWith('/watchlist'));
    }
    fixture.destroy();
    http.verify();
  }));
  it('stops a stalled stock refresh and retains the displayed snapshot', fakeAsync(() => {
    const fixture = TestBed.createComponent(StocksComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(emptySnapshot('FPT'));
    fixture.componentInstance.refresh();
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    const stalled = http.expectOne((r) => r.url.endsWith('/FPT/analysis'));
    tick(180001);
    expect(stalled.cancelled).toBeTrue();
    expect(fixture.componentInstance.loading()).toBeFalse();
    expect(fixture.componentInstance.data()?.snapshotId).toBe('FPT-snapshot');
    expect(fixture.componentInstance.error()).toContain('phản hồi chậm');
    fixture.destroy();
    http.verify({ ignoreCancelled: true });
  }));
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'stocks/:symbol', component: StocksComponent },
          { path: 'analyze', component: EmptyComponent },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ lang: 'vi' }),
      ],
    }),
  );
  it('cancels old symbols, ignores lottery category and keeps position tied to the displayed snapshot', async () => {
    const harness = await RouterTestingHarness.create();
    const http = TestBed.inject(HttpTestingController);
    await harness.navigateByUrl('/stocks/FPT', StocksComponent);
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    const old = http.expectOne((r) => r.url.endsWith('/FPT/analysis'));
    const page = await harness.navigateByUrl('/stocks/MBB', StocksComponent);
    expect(old.cancelled).toBeTrue();
    const current = http.expectOne((r) => r.url.endsWith('/MBB/analysis'));
    expect(current.request.params.has('category')).toBeFalse();
    current.flush(emptySnapshot('MBB'));
    TestBed.inject(CategoryService).setCategory('POWER');
    http.expectNone((r) => r.url.includes('/api/stocks'));
    page.input = {
      purchaseDate: '2026-10-01',
      purchasePrice: 20000,
      quantity: null,
      maxLossPercent: null,
      sellableQuantity: null,
    };
    page.analyzePosition();
    const position = http.expectOne((r) => r.url.endsWith('/MBB/position-analysis'));
    expect(position.request.params.get('snapshotId')).toBe('MBB-snapshot');
    page.setMode('INTRADAY');
    expect(position.cancelled).toBeTrue();
    const next = http.expectOne((r) => r.url.endsWith('/MBB/analysis'));
    expect(next.request.params.get('mode')).toBe('INTRADAY');
    next.flush(emptySnapshot('MBB'));
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Chưa đủ dữ liệu');
    http.verify({ ignoreCancelled: true });
  });
  it('keeps lottery controls in page context and preserves the category across stock routes', async () => {
    const harness = await RouterTestingHarness.create();
    const http = TestBed.inject(HttpTestingController);
    const header = TestBed.createComponent(HeaderComponent);
    await harness.navigateByUrl('/stocks/FPT', StocksComponent);
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(emptySnapshot('FPT'));
    header.detectChanges();
    expect(TestBed.inject(SectionService).stocks()).toBeTrue();
    expect(header.nativeElement.querySelector('.category-picker')).toBeNull();
    TestBed.inject(CategoryService).setCategory('POWER');
    await harness.navigateByUrl('/analyze');
    header.detectChanges();
    expect(TestBed.inject(SectionService).stocks()).toBeFalse();
    expect(TestBed.inject(CategoryService).category()).toBe('POWER');
    expect(header.nativeElement.querySelector('.category-picker')).toBeNull();
    header.destroy();
    http.verify();
  });
  it('submits a position without fee fields and displays reference price PnL', async () => {
    const harness = await RouterTestingHarness.create();
    const http = TestBed.inject(HttpTestingController);
    const page = await harness.navigateByUrl('/stocks/VIC', StocksComponent);
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    const data = emptySnapshot('VIC');
    data.data.unit = 'VND';
    data.data.candles = [
      {
        time: Date.parse('2026-11-03T02:00:00Z') / 1000,
        open: 230000,
        high: 235000,
        low: 229000,
        close: 234000,
        volume: 1000,
        closed: true,
      },
    ];
    http.expectOne((r) => r.url.endsWith('/VIC/analysis')).flush(data);
    harness.detectChanges();
    expect(harness.routeNativeElement?.querySelector('input[name="buyFee"]')).toBeNull();
    expect(harness.routeNativeElement?.querySelector('input[name="sellCost"]')).toBeNull();
    expect(harness.routeNativeElement?.querySelector('input[name="maxLoss"]')).toBeNull();
    expect(
      !!harness.routeNativeElement?.querySelector(
        '.main-grid .position-workspace app-stock-position-form',
      ),
    ).toBeTrue();
    page.input = {
      purchaseDate: '',
      purchasePrice: 234000,
      quantity: 200,
      sellableQuantity: 200,
      maxLossPercent: 2,
    };
    page.analyzePosition();
    const request = http.expectOne((r) => r.url.endsWith('/VIC/position-analysis'));
    expect(request.request.body.buyFeePercent).toBeUndefined();
    expect(request.request.body.sellCostPercent).toBeUndefined();
    expect(request.request.body.maxLossPercent).toBe(2);
    expect(request.request.body.purchaseDate).toBeNull();
    request.flush({
      snapshotId: data.snapshotId,
      status: 'WATCH',
      price: 234000,
      changePercent: 0,
      grossPnl: 0,
      estimatedNetPnl: null,
      breakEven: null,
      reasons: ['Kết quả tính tham khảo; chưa tính phí, thuế.'],
    });
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('0.00%');
    expect(harness.routeNativeElement?.textContent).not.toContain('Lãi/lỗ ròng');
    http.verify();
  });
  it('puts classifier scores in collapsed details and leads with conditional price guidance', async () => {
    const harness = await RouterTestingHarness.create();
    const http = TestBed.inject(HttpTestingController);
    await harness.navigateByUrl('/stocks/FPT', StocksComponent);
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    const data = emptySnapshot('FPT');
    data.analysis.learning.buyScore = 0.8;
    data.analysis.learning.holdScore = 0.1;
    data.analysis.learning.sellScore = 0.1;
    data.analysis.recommendation = {
      version: 'test',
      action: 'WATCH',
      title: 'Chờ xác nhận vượt kháng cự',
      summary: 'Cần nến đóng và khối lượng xác nhận.',
      unit: 'VND',
      asOf: null,
      evaluatedPrice: null,
      buyAbove: 51000,
      sellBelow: 48000,
      stopLoss: 49000,
      takeProfit: 55000,
      volumeRatio: null,
      volumeConfirmation: 1.2,
      buyConditions: [],
      sellConditions: [],
      cautions: [],
    };
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(data);
    harness.detectChanges();
    const root = harness.routeNativeElement!;
    expect(root.querySelector('app-stock-recommendation')?.textContent).toContain(
      'Chờ xác nhận vượt kháng cự',
    );
    const details = root.querySelector('.score-grid')?.closest('details');
    expect(details?.open).toBeFalse();
    expect(root.textContent).toContain('không phải tỷ lệ vốn nên mua/bán');
    http.verify();
  });
  it('keeps separate inputs per symbol and refreshes an accepted position without clearing it', async () => {
    const harness = await RouterTestingHarness.create();
    const http = TestBed.inject(HttpTestingController);
    const page = await harness.navigateByUrl('/stocks/FPT', StocksComponent);
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(emptySnapshot('FPT'));
    page.input.purchasePrice = 30000;
    page.input.quantity = 1000;
    page.input.sellableQuantity = 400;
    page.resetPosition();
    expect(page.input.plan).toBeUndefined();
    page.analyzePosition();
    const result = {
      snapshotId: 'FPT-snapshot',
      status: 'WATCH',
      price: 27000,
      changePercent: -10,
      grossPnl: -3000000,
      estimatedNetPnl: null,
      breakEven: null,
      reasons: [],
    };
    http.expectOne((r) => r.url.endsWith('/FPT/position-analysis')).flush(result);
    page.refresh();
    expect(page.position()?.grossPnl).toBe(-3000000);
    expect(page.positionStale()).toBeTrue();
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    const next = { ...emptySnapshot('FPT'), snapshotId: 'FPT-new' };
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(next);
    const recalculation = http.expectOne((r) => r.url.endsWith('/FPT/position-analysis'));
    expect(recalculation.request.params.get('snapshotId')).toBe('FPT-new');
    recalculation.flush({ ...result, snapshotId: 'FPT-new', grossPnl: -2000000 });
    expect(page.positionStale()).toBeFalse();
    await harness.navigateByUrl('/stocks/MBB', StocksComponent);
    http.expectOne((r) => r.url.endsWith('/MBB/analysis')).flush(emptySnapshot('MBB'));
    expect(page.input.purchasePrice).toBeNull();
    expect(page.position()).toBeNull();
    page.input.purchasePrice = 20000;
    await harness.navigateByUrl('/stocks/FPT', StocksComponent);
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(next);
    http
      .expectOne((r) => r.url.endsWith('/FPT/position-analysis'))
      .flush({ ...result, snapshotId: 'FPT-new' });
    expect(page.input.purchasePrice).toBe(30000);
    expect(page.input.quantity).toBe(1000);
    http.verify();
  });
  it('cancels position calculation after editing and never accepts a mismatched snapshot', async () => {
    const harness = await RouterTestingHarness.create();
    const http = TestBed.inject(HttpTestingController);
    const page = await harness.navigateByUrl('/stocks/FPT', StocksComponent);
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(emptySnapshot('FPT'));
    page.input.purchasePrice = 30000;
    page.analyzePosition();
    const old = http.expectOne((r) => r.url.endsWith('/FPT/position-analysis'));
    page.input.purchasePrice = 31000;
    page.resetPosition();
    expect(old.cancelled).toBeTrue();
    page.analyzePosition();
    http
      .expectOne((r) => r.url.endsWith('/FPT/position-analysis'))
      .flush({ snapshotId: 'OTHER', status: 'SELL', reasons: [] });
    expect(page.position()).toBeNull();
    expect(page.positionError()).toContain('snapshot');
    http.verify({ ignoreCancelled: true });
  });
});
