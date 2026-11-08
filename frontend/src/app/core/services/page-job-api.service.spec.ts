import { BackendReadinessService } from './backend-readiness.service';
import { of as readyNow } from 'rxjs';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PageJobApiService, PageJob } from './page-job-api.service';
import { environment } from '../../../environments/environment';

describe('Async page jobs', () => {
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
  const id = 'l-00000000-0000-0000-0000-000000000001';
  const url = `${environment.apiUrl || ''}/api/page-jobs/${id}`;
  const pending: PageJob<{ value: number }> = {
    id,
    status: 'QUEUED',
    data: null,
    updatedAt: '2026-11-08T10:00:00Z',
  };
  let http: HttpTestingController;
  let hidden: jasmine.Spy;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    hidden = spyOnProperty(document, 'hidden', 'get').and.returnValue(false);
  });
  afterEach(() => http.verify({ ignoreCancelled: true }));
  it('pauses status polling while the tab is hidden and resumes when it is visible', fakeAsync(() => {
    const transport = TestBed.inject(HttpClient);
    let result: { value: number } | undefined;
    TestBed.inject(PageJobApiService)
      .follow(transport.get<PageJob<{ value: number }>>('/read'))
      .subscribe((value) => (result = value));
    http.expectOne('/read').flush(pending);
    hidden.and.returnValue(true);
    tick(6000);
    http.expectNone(url);
    hidden.and.returnValue(false);
    tick(2000);
    http.expectOne(url).flush({ ...pending, status: 'SUCCEEDED', data: { value: 9 } });
    expect(result).toEqual({ value: 9 });
  }));
  it('submits once and polls sequentially until the result is available', fakeAsync(() => {
    const client = TestBed.inject(PageJobApiService);
    const transport = TestBed.inject(HttpClient);
    let result: { value: number } | undefined;
    client
      .follow(transport.post<PageJob<{ value: number }>>('/submit', {}))
      .subscribe((value) => (result = value));
    http.expectOne('/submit').flush(pending, { status: 202, statusText: 'Accepted' });
    expect(result).toBeUndefined();
    tick(2000);
    const poll = http.expectOne(url);
    tick(6000);
    http.expectNone(url);
    poll.flush({ ...pending, status: 'RUNNING' });
    tick(2000);
    http.expectOne(url).flush({ ...pending, status: 'SUCCEEDED', data: { value: 7 } });
    expect(result).toEqual({ value: 7 });
    tick(6000);
    http.expectNone(url);
    http.expectNone('/submit');
  }));
  it('cancels polling when leaving a page and does not replay a mutation', fakeAsync(() => {
    const transport = TestBed.inject(HttpClient);
    const subscription = TestBed.inject(PageJobApiService)
      .follow(transport.post<PageJob<unknown>>('/submit', {}))
      .subscribe();
    http.expectOne('/submit').flush(pending);
    tick(2000);
    const poll = http.expectOne(url);
    subscription.unsubscribe();
    expect(poll.cancelled).toBeTrue();
    tick(60000);
    http.expectNone('/submit');
    http.expectNone(url);
  }));
  it('ends failed jobs instead of leaving a spinner running', fakeAsync(() => {
    const transport = TestBed.inject(HttpClient);
    let error = '';
    TestBed.inject(PageJobApiService)
      .follow(transport.get<PageJob<unknown>>('/read'))
      .subscribe({ error: (value) => (error = value.message) });
    http
      .expectOne('/read')
      .flush({ ...pending, status: 'FAILED', message: 'Đã hết thời gian xử lý' });
    expect(error).toContain('hết thời gian');
    tick(6000);
    http.expectNone(url);
  }));
});
