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
    TestBed.configureTestingModule({ imports: [ForecastsComponent], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    fixture = TestBed.createComponent(ForecastsComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); http.verify({ ignoreCancelled: true }); });

  it('loads a single compact endpoint without starting reconciliation', () => {
    http.expectOne(overview()).flush(payload);
    http.expectNone(base); http.expectNone(`${base}/evaluation?category=MEGA`); http.expectNone(`${base}/reconcile`);
    expect(fixture.componentInstance.busy).toBeFalse();
  });
  it('cancels the previous category request and cancels requests when leaving the page', () => {
    const previous = http.expectOne(overview());
    fixture.componentInstance.category = 'POWER'; fixture.componentInstance.load();
    expect(previous.cancelled).toBeTrue();
    const current = http.expectOne(overview('POWER'));
    fixture.destroy();
    expect(current.cancelled).toBeTrue();
  });
  it('does not retry 503 automatically and allows an explicit retry', () => {
    http.expectOne(overview()).flush({}, { status: 503, statusText: 'Service Unavailable' });
    expect(fixture.componentInstance.error).toContain('quá tải');
    expect(fixture.componentInstance.busy).toBeFalse();
    fixture.componentInstance.load(); http.expectOne(overview()).flush(payload);
    expect(fixture.componentInstance.error).toBe('');
  });
  it('prevents duplicate capture submissions and refreshes once after completion', () => {
    http.expectOne(overview()).flush(payload);
    fixture.componentInstance.capture(); fixture.componentInstance.capture();
    const capture = http.expectOne(`${base}/capture`);
    expect(capture.request.method).toBe('POST');
    capture.flush({status: 'BUSY', message: 'Đang xử lý'});
    http.expectOne(overview()).flush(payload);
    expect(fixture.componentInstance.message).toBe('Đang xử lý');
  });
  it('uses the shared category and does not refetch when the same category is selected', () => {
    http.expectOne(overview()).flush(payload);
    const analyze = TestBed.inject(AnalyzeService);
    analyze.setCategory('POWER');
    http.expectOne(overview('POWER')).flush(payload);
    expect(fixture.componentInstance.category).toBe('POWER');
    analyze.setCategory('POWER'); http.expectNone(overview('POWER'));
  });
  it('derives summary and matched balls from measured results without counting pending tickets as hits', () => {
    http.expectOne(overview()).flush({ ...payload, totalRows: 3, rows: [
      {id:'a',targetDate:'2026-10-25',algorithm:'temporal_learning',numbers:[1,2,3,4,5,6],createdAt:'2026-10-24T12:00:00Z',settlement:{status:'SETTLED',actualNumbers:[1,2,3,7,8,9],matched:3}},
      {id:'b',targetDate:'2026-10-28',algorithm:'temporal_learning',numbers:[1,2,3,4,5,6],createdAt:'2026-10-26T12:00:00Z'}
    ], evaluation:{groups:[{algorithm:'temporal_learning',modelVersion:'v2',configurationHash:'test-config',savedForecasts:3,evaluatedDraws:1,pendingOrUnavailable:2,matchHistogram:[0,0,0,1,0,0,0]}]} });
    const component = fixture.componentInstance;
    expect(component.evaluatedCount).toBe(1); expect(component.pendingCount).toBe(2); expect(component.hit3Count).toBe(1);
    expect(component.isMatched(component.rows[0],1)).toBeTrue(); expect(component.isMatched(component.rows[1],1)).toBeFalse();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.number-ball.matched').length).toBe(3);
    component.statusFilter='PENDING'; expect(component.filteredRows.map(row=>row.id)).toEqual(['b']);
  });
});
