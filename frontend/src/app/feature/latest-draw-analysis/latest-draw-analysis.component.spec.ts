import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { LatestDrawAnalysisComponent } from './latest-draw-analysis.component';
import { AnalyzeService } from '../../core/services/analyze.service';
import { DrawReport, AnalyzedTicketGroup } from '../../core/models/draw-report.model';

describe('Snapshot draw analysis', () => {
  let http: HttpTestingController;
  const report = (category = 'POWER', algorithm = 'temporal_learning'): DrawReport => ({
    schemaVersion: 'draw-report-v1',
    context: { category, algorithm, drawDate: '2026-10-29', generatedAt: '2026-10-29T14:40:00Z' },
    status: 'SUCCESS',
    message: null,
    draw: {
      numbers: [9, 29, 33, 38, 45, 46],
      specialNumber: 53,
      fingerprint: 'frozen-result',
      quality: 'SOURCE_MATCHED',
      sourceUrl: 'https://example.com/feed',
      updatedAt: null,
    },
    evidence: [],
    selectedEvidenceId: null,
    numberDetails: [],
    ticketGroups: [],
    evaluation: [],
    warnings: ['Chưa có snapshot.'],
  });
  const checked: AnalyzedTicketGroup = {
    id: 'checked',
    label: 'Vé đã dò',
    timing: 'CHECKED_AFTER_DRAW',
    invalidTicketCount: 0,
    frequency: null,
    random: null,
    tickets: [
      {
        id: 'checked:83',
        source: 'CHECKED_AFTER_DRAW',
        sourceId: null,
        label: 'Vé #83',
        numbers: [8, 15, 29, 33, 43, 49],
        originalNumbers: null,
        edited: false,
        matchedNumbers: [29, 33],
        missedNumbers: [8, 15, 43, 49],
        matchedCount: 2,
        matchedSpecial: false,
        prize: 'KHÔNG TRÚNG',
      },
    ],
    portfolio: {
      enteredTicketCount: 1,
      ticketCount: 1,
      duplicateTicketCount: 0,
      uniqueNumberCount: 6,
      uniqueNumbers: [8, 15, 29, 33, 43, 49],
      rawMatchedOccurrences: 2,
      coverage: [29, 33],
      coverageCount: 2,
      coveragePercent: 33.3,
      bestMatched: 2,
      averageMatched: 2,
      matchHistogram: [0, 0, 1, 0, 0, 0, 0],
      overlaps: [[6]],
      numberUsage: { 8: 1, 15: 1, 29: 1, 33: 1, 43: 1, 49: 1 },
    },
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify({ ignoreCancelled: true }));
  function mount(category = 'POWER') {
    TestBed.inject(AnalyzeService).setCategory(category);
    const f = TestBed.createComponent(LatestDrawAnalysisComponent);
    f.detectChanges();
    http
      .expectOne((r) => r.url.endsWith('/algorithms'))
      .flush([{ id: 'temporal_learning', name: 'Học liên kết' }]);
    return f;
  }
  it('cancels old category, date and algorithm requests and destroys outstanding reads', () => {
    const f = mount('MEGA');
    const old = http.expectOne((r) => r.url.endsWith('/draw-report'));
    TestBed.inject(AnalyzeService).setCategory('POWER');
    expect(old.cancelled).toBeTrue();
    const power = http.expectOne((r) => r.params.get('category') === 'POWER');
    f.componentInstance.dateDraft = '2026-10-27';
    f.componentInstance.algorithmDraft = 'bayesian_graph';
    f.componentInstance.fetchAnalysis();
    expect(power.cancelled).toBeTrue();
    const current = http.expectOne(
      (r) =>
        r.params.get('date') === '2026-10-27' && r.params.get('algorithm') === 'bayesian_graph',
    );
    f.destroy();
    expect(current.cancelled).toBeTrue();
    http.expectNone((r) => r.method !== 'GET');
  });
  it('renders checked tickets as checked evidence and exports the same totals without changing configuration', () => {
    const f = mount();
    const r = report();
    r.ticketGroups = [checked];
    http.expectOne((req) => req.url.endsWith('/draw-report')).flush(r);
    f.detectChanges();
    const text = f.nativeElement.textContent;
    expect(text).toContain('vé tốt nhất');
    expect(text).toContain('chưa xác định nguồn dự đoán');
    expect(text).toContain('Chưa có snapshot');
    expect(f.componentInstance.reportText()).toContain('độ phủ 2/6 [29, 33]');
    expect(f.componentInstance.reportText()).toContain('2 lượt khớp');
    expect(f.nativeElement.querySelector('button')?.textContent).not.toContain(
      'Cập nhật thuật toán',
    );
    http.expectNone((req) => req.method === 'POST');
    f.destroy();
  });
  it('rejects incomplete, decimal, out-of-range and negative tickets without silently discarding tokens', () => {
    const f = mount();
    http.expectOne((r) => r.url.endsWith('/draw-report')).flush(report());
    for (const text of [
      '1 2 3 4 5',
      '1 2 3 4 5 5',
      '1 2 3 4 5 56',
      '1 2 3 4 5 6.5',
      '1 2 3 4 5 -6',
    ]) {
      f.componentInstance.manualText = text;
      f.componentInstance.evaluateManual();
      expect(f.componentInstance.manualError()).not.toBe('');
    }
    http.expectNone((r) => r.method === 'POST');
    f.destroy();
  });
  it('uses the pure ticket endpoint and cancels a manual check when the report context changes', () => {
    const f = mount();
    http.expectOne((r) => r.url.endsWith('/draw-report')).flush(report());
    f.componentInstance.manualText = '08 15 29 33 43 49';
    f.componentInstance.evaluateManual();
    const check = http.expectOne((r) => r.url.endsWith('/evaluate-tickets'));
    expect(check.request.body).toEqual({
      category: 'POWER',
      date: '2026-10-29',
      tickets: [[8, 15, 29, 33, 43, 49]],
    });
    f.componentInstance.dateDraft = '2026-10-27';
    f.componentInstance.fetchAnalysis();
    expect(check.cancelled).toBeTrue();
    expect(f.componentInstance.manualGroup()).toBeNull();
    http
      .expectOne((r) => r.url.endsWith('/draw-report'))
      .flush({ ...report(), context: { ...report().context, drawDate: '2026-10-27' } });
    http.expectNone((r) => r.url.includes('suggestions') || r.url.includes('update-algorithm'));
    f.destroy();
  });
  it('keeps controls being edited separate from the context in the displayed report', () => {
    const f = mount();
    http.expectOne((r) => r.url.endsWith('/draw-report')).flush(report());
    f.componentInstance.algorithmDraft = 'deep_stacking';
    f.componentInstance.dateDraft = '2026-10-27';
    expect(f.componentInstance.reportText()).toContain('temporal_learning');
    expect(f.componentInstance.reportText()).toContain('2026-10-29');
    f.destroy();
  });
});
