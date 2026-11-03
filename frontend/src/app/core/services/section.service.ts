import { Injectable, inject } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SectionService {
  private readonly router = inject(Router);
  private isStocks(url: string) {
    return /^\/stocks(?:\/|\?|#|$)/.test(url);
  }
  readonly stocks = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => this.isStocks(e.urlAfterRedirects)),
    ),
    { initialValue: this.isStocks(this.router.url) },
  );
}
