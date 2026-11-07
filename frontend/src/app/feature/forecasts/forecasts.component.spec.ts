import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ForecastsComponent } from './forecasts.component';
import { environment } from '../../../environments/environment';
import { provideRouter } from '@angular/router';
import { AnalyzeService } from '../../core/services/analyze.service';

describe('ForecastsComponent requests', () => {
  let fixture: ComponentFixture<ForecastsComponent>;
  let http: HttpTestingController;
  const base = `${environment.apiUrl || ''}/api/forecasts`;
  const overview = (category = 'MEGA') => `${base}/overview?category=${category}&page=0&size=50`;
  const payload = { rows: [], totalRows: 0, evaluation: { groups: [] }, enabled: true };
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ForecastsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(ForecastsComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify({ ignoreCancelled: true });
  });

  it('loads a single compact endpoint without starting reconciliation', () => {
    http.expectOne(overview()).flush(payload);
    http.expectNone(base);
    http.expectNone(`${base}/evaluation?category=MEGA`);
    http.expectNone(`${base}/reconcile`);
    expect(fixture.componentInstance.busy()).toBeFalse();
  });
  it('keeps the threshold denominator in evaluated draws and separates version filters', () => {
    http.expectOne(overview()).flush({
      ...payload,
      evaluation: {
        groups: [
          {
            cohort: 'v1',
            algorithm: 'test',
            modelVersion: 'v1',
            configurationHash: 'one',
            evaluatedDraws: 4,
            savedForecasts: 6,
            pendingOrUnavailable: 2,
            matchHistogram: [1, 0, 0, 1, 1, 1, 0],
          },
          {
            cohort: 'v2',
            algorithm: 'test',
            modelVersion: 'v2',
            configurationHash: 'two',
            evaluatedDraws: 0,
            savedForecasts: 1,
            pendingOrUnavailable: 1,
            matchHistogram: [0, 0, 0, 0, 0, 0, 0],
          },
        ],
      },
    });
    const view = fixture.componentInstance;
    view.threshold.set(4);
    expect(view.thresholdCount()).toBe(2);
    expect(view.groupRate(view.groups()[0])).toBe(50);
    expect(view.groupRate(view.groups()[1])).toBeNull();
    view.cohortFilter.set('v2');
    expect(view.visibleGroups().map((g) => g.cohort)).toEqual(['v2']);
  });
  it('cancels the previous category request and cancels requests when leaving the page', () => {
    const previous = http.expectOne(overview());
    fixture.componentInstance.category.set('POWER');
    fixture.componentInstance.load();
    expect(previous.cancelled).toBeTrue();
    const current = http.expectOne(overview('POWER'));
    fixture.destroy();
    expect(current.cancelled).toBeTrue();
  });
  it('does not retry 503 automatically and allows an explicit retry', () => {
    http.expectOne(overview()).flush({}, { status: 503, statusText: 'Service Unavailable' });
    expect(fixture.componentInstance.error()).toContain('quá tải');
    expect(fixture.componentInstance.busy()).toBeFalse();
    fixture.componentInstance.load();
    http.expectOne(overview()).flush(payload);
    expect(fixture.componentInstance.error()).toBe('');
  });
  it('applies date boundaries to the compact API and rejects a reversed interval', () => {
    http.expectOne(overview()).flush(payload);
    const view = fixture.componentInstance;
    view.fromDate.set('2026-10-01');
    view.toDate.set('2026-10-31');
    view.load();
    http
      .expectOne(
        (r) =>
          r.url.endsWith('/overview') &&
          r.params.get('fromDate') === '2026-10-01' &&
          r.params.get('toDate') === '2026-10-31',
      )
      .flush(payload);
    view.fromDate.set('2026-11-01');
    view.load();
    expect(view.error()).toContain('Ngày bắt đầu');
    http.expectNone((r) => r.url.endsWith('/overview'));
  });
  it('prevents duplicate capture submissions and refreshes once after completion', () => {
    http.expectOne(overview()).flush(payload);
    fixture.componentInstance.capture();
    fixture.componentInstance.capture();
    const capture = http.expectOne(`${base}/capture`);
    expect(capture.request.method).toBe('POST');
    capture.flush({ status: 'BUSY', message: 'Đang xử lý' });
    http.expectOne(overview()).flush(payload);
    expect(fixture.componentInstance.message()).toBe('Đang xử lý');
  });
  it('uses the shared category and does not refetch when the same category is selected', () => {
    http.expectOne(overview()).flush(payload);
    const analyze = TestBed.inject(AnalyzeService);
    analyze.setCategory('POWER');
    http.expectOne(overview('POWER')).flush(payload);
    expect(fixture.componentInstance.category()).toBe('POWER');
    analyze.setCategory('POWER');
    http.expectNone(overview('POWER'));
  });
  it('derives summary and matched balls from measured results without counting pending tickets as hits', () => {
    http.expectOne(overview()).flush({
      ...payload,
      totalRows: 3,
      rows: [
        {
          id: 'a',
          targetDate: '2026-10-25',
          algorithm: 'temporal_learning',
          numbers: [1, 2, 3, 4, 5, 6],
          createdAt: '2026-10-24T12:00:00Z',
          settlement: { status: 'SETTLED', actualNumbers: [1, 2, 3, 7, 8, 9], matched: 3 },
        },
        {
          id: 'b',
          targetDate: '2026-10-28',
          algorithm: 'temporal_learning',
          numbers: [1, 2, 3, 4, 5, 6],
          createdAt: '2026-10-26T12:00:00Z',
        },
      ],
      evaluation: {
        groups: [
          {
            algorithm: 'temporal_learning',
            modelVersion: 'v2',
            configurationHash: 'test-config',
            savedForecasts: 3,
            evaluatedDraws: 1,
            pendingOrUnavailable: 2,
            matchHistogram: [0, 0, 0, 1, 0, 0, 0],
          },
        ],
      },
    });
    const component = fixture.componentInstance;
    expect(component.evaluatedCount()).toBe(1);
    expect(component.pendingCount()).toBe(2);
    expect(component.hit3Count()).toBe(1);
    expect(component.isMatched(component.rows()[0], 1)).toBeTrue();
    expect(component.isMatched(component.rows()[1], 1)).toBeFalse();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.number-ball.matched').length).toBe(3);
    component.statusFilter.set('PENDING');
    expect(component.filteredRows().map((row) => row.id)).toEqual(['b']);
  });
});
