import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import {
  Observable,
  defer,
  finalize,
  map,
  of,
  retry,
  shareReplay,
  tap,
  throwError,
  timeout,
  timer,
} from 'rxjs';
import { environment } from '../../../environments/environment';

/** One shared liveness check before page jobs, including the first POST after a Free service wakes up. */
@Injectable({ providedIn: 'root' })
export class BackendReadinessService {
  readonly waiting = signal(false);
  private readonly http = inject(HttpClient);
  private healthyUntil = 0;
  private warming?: Observable<void>;
  invalidate() {
    this.healthyUntil = 0;
  }
  ready(): Observable<void> {
    return defer(() => {
      if (Date.now() < this.healthyUntil) return of(undefined);
      if (!this.warming) {
        this.waiting.set(true);
        this.warming = defer(() =>
          this.http.get<{ status: string }>(`${environment.apiUrl || ''}/api/health`),
        ).pipe(
          timeout(15_000),
          map((value) => {
            if (value.status !== 'UP') throw new Error('Máy chủ chưa sẵn sàng.');
          }),
          retry({
            count: 35,
            delay: (error, attempt) =>
              error instanceof HttpErrorResponse &&
              error.status >= 400 &&
              error.status < 500 &&
              error.status !== 429
                ? throwError(() => error)
                : timer(Math.min(attempt * 1000, 5000)),
          }),
          timeout(180_000),
          tap(() => (this.healthyUntil = Date.now() + 60_000)),
          finalize(() => {
            this.warming = undefined;
            this.waiting.set(false);
          }),
          shareReplay({ bufferSize: 1, refCount: true }),
        );
      }
      return this.warming;
    });
  }
}
