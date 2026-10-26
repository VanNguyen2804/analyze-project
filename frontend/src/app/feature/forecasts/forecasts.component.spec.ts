import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ForecastsComponent } from './forecasts.component';
import { environment } from '../../../environments/environment';

describe('ForecastsComponent requests', () => {
  let fixture: ComponentFixture<ForecastsComponent>;
  let http: HttpTestingController;
  const base = `${environment.apiUrl || ''}/api/forecasts`;
  const overview = (category = 'MEGA') => `${base}/overview?category=${category}&page=0&size=50`;
  const payload = { rows: [], totalRows: 0, evaluation: { groups: [] }, enabled: true };
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ForecastsComponent], providers: [provideHttpClient(), provideHttpClientTesting()] });
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
});
