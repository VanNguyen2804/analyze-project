import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { API_TIMEOUT_MS, apiTimeoutInterceptor } from './api-timeout.interceptor';

describe('API cold start timeout', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiTimeoutInterceptor])),
        provideHttpClientTesting(),
      ],
    }),
  );

  it('cancels a stalled cross-origin write without retrying it', fakeAsync(() => {
    const errors: unknown[] = [];
    TestBed.inject(HttpClient)
      .post('https://backend.example/api/analyze/add-result', {})
      .subscribe({ error: (error) => errors.push(error) });
    const http = TestBed.inject(HttpTestingController);
    const pending = http.expectOne('https://backend.example/api/analyze/add-result');
    tick(API_TIMEOUT_MS + 1);
    expect(pending.cancelled).toBeTrue();
    expect(errors.length).toBe(1);
    http.expectNone('https://backend.example/api/analyze/add-result');
    http.verify();
  }));
});
