import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
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
      buyFeePercent: null,
      sellCostPercent: null,
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
  it('hides the lottery selector on direct stock routes and restores it on lottery routes', async () => {
    const harness = await RouterTestingHarness.create();
    const http = TestBed.inject(HttpTestingController);
    const header = TestBed.createComponent(HeaderComponent);
    await harness.navigateByUrl('/stocks/FPT', StocksComponent);
    http.expectOne((r) => r.url.endsWith('/watchlist')).flush([]);
    http.expectOne((r) => r.url.endsWith('/FPT/analysis')).flush(emptySnapshot('FPT'));
    header.detectChanges();
    expect(TestBed.inject(SectionService).stocks()).toBeTrue();
    expect(header.nativeElement.querySelector('.category-picker')).toBeNull();
    await harness.navigateByUrl('/analyze');
    header.detectChanges();
    expect(header.nativeElement.querySelector('.category-picker')).not.toBeNull();
    header.destroy();
    http.verify();
  });
});
