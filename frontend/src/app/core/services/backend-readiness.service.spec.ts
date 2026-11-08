import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BackendReadinessService } from './backend-readiness.service';
import { PageJobApiService } from './page-job-api.service';
import { environment } from '../../../environments/environment';

describe('Backend cold start', () => {
  const health = `${environment.apiUrl || ''}/api/health`;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify({ ignoreCancelled: true }));
  it('shares warm-up and sends a mutation only after the backend responds UP', fakeAsync(() => {
    const client = TestBed.inject(PageJobApiService),
      transport = TestBed.inject(HttpClient);
    const results: number[] = [];
    client.follow(transport.get<number>('/quotes')).subscribe((value) => results.push(value));
    client
      .follow(transport.post<number>('/suggestions', {}))
      .subscribe((value) => results.push(value));
    http.expectNone('/quotes');
    http.expectNone('/suggestions');
    expect(TestBed.inject(BackendReadinessService).waiting()).toBeTrue();
    http.expectOne(health).flush({}, { status: 503, statusText: 'Starting' });
    tick(1000);
    http.expectOne(health).flush({ status: 'UP' });
    http.expectOne('/quotes').flush(1);
    http.expectOne('/suggestions').flush(2);
    expect(results).toEqual([1, 2]);
    expect(TestBed.inject(BackendReadinessService).waiting()).toBeFalse();
  }));
  it('does not retry an accepted mutation when its response is lost', fakeAsync(() => {
    let failed = false;
    const transport = TestBed.inject(HttpClient);
    TestBed.inject(PageJobApiService)
      .follow(transport.post('/suggestions', {}))
      .subscribe({ error: () => (failed = true) });
    http.expectOne(health).flush({ status: 'UP' });
    http.expectOne('/suggestions').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(failed).toBeTrue();
    tick(10000);
    http.expectNone('/suggestions');
    http.expectNone(health);
  }));
  it('stops warm-up when the last page subscriber leaves', () => {
    const subscription = TestBed.inject(BackendReadinessService).ready().subscribe();
    const request = http.expectOne(health);
    subscription.unsubscribe();
    expect(request.cancelled).toBeTrue();
    expect(TestBed.inject(BackendReadinessService).waiting()).toBeFalse();
  });
});
