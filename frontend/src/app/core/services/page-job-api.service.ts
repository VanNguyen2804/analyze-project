import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import {
  EMPTY,
  Observable,
  TimeoutError,
  catchError,
  defer,
  expand,
  filter,
  map,
  switchMap,
  take,
  takeUntil,
  throwError,
  timer,
  timeout,
} from 'rxjs';
import { environment } from '../../../environments/environment';
import { BackendReadinessService } from './backend-readiness.service';

export interface PageJob<T> {
  id: string;
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  data: T | null;
  message?: string;
  updatedAt: string;
}

/** Poll only lightweight status. Unsubscribe stops polling, not an accepted server job. */
@Injectable({ providedIn: 'root' })
export class PageJobApiService {
  private readonly http = inject(HttpClient);
  private readonly backend = inject(BackendReadinessService);
  private isJob<T>(value: T | PageJob<T>): value is PageJob<T> {
    return (
      !!value &&
      typeof value === 'object' &&
      'id' in value &&
      'status' in value &&
      typeof value.id === 'string' &&
      /^[sl]-[a-f0-9-]{36}$/.test(value.id) &&
      ['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(String(value.status))
    );
  }
  follow<T>(request: Observable<T | PageJob<T>>): Observable<T> {
    return defer(() =>
      this.backend
        .ready()
        .pipe(
          switchMap(() =>
            request.pipe(
              timeout(180_000),
              expand((value, attempt) => {
                if (!this.isJob(value) || value.status === 'SUCCEEDED') return EMPTY;
                if (value.status === 'FAILED')
                  return throwError(
                    () => new Error(value.message || 'Không hoàn tất tác vụ. Hãy thử lại.'),
                  );
                const delay = Math.min(2000 + Math.floor(attempt / 5) * 1000, 10000);
                return timer(delay, delay).pipe(
                  filter(() => !document.hidden),
                  take(1),
                  switchMap(() =>
                    this.http
                      .get<PageJob<T>>(`${environment.apiUrl || ''}/api/page-jobs/${value.id}`)
                      .pipe(timeout(30_000)),
                  ),
                );
              }, 1),
              filter((value) => !this.isJob(value) || value.status === 'SUCCEEDED'),
              map((value) => (this.isJob(value) ? (value.data as T) : value)),
              take(1),
              takeUntil(timer(600_000).pipe(switchMap(() => throwError(() => new TimeoutError())))),
            ),
          ),
        )
        .pipe(
          catchError((error) => {
            if (
              error instanceof HttpErrorResponse &&
              (error.status === 0 || error.status === 200 || error.status >= 500)
            )
              this.backend.invalidate();
            return throwError(() => error);
          }),
        ),
    );
  }
}
