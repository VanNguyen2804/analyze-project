import { HttpInterceptorFn } from '@angular/common/http';
import { timeout } from 'rxjs';

// Allow a sleeping Free backend to start, but do not leave page requests pending forever.
export const API_TIMEOUT_MS = 180_000;
export const apiTimeoutInterceptor: HttpInterceptorFn = (request, next) => {
  const response = next(request);
  return new URL(request.url, document.baseURI).pathname.startsWith('/api/')
    ? response.pipe(timeout({ each: API_TIMEOUT_MS }))
    : response;
};
