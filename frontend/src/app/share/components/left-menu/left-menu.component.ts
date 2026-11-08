import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
  HostListener,
} from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  filter,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { CategoryService } from '../../../core/services/category.service';
import { AnalyzeService } from '../../../core/services/analyze.service';
import { SectionService } from '../../../core/services/section.service';
import { NavigationService } from '../../../core/services/navigation.service';

@Component({
  selector: 'app-left-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './left-menu.component.html',
  styleUrl: './left-menu.component.css',
})
export class LeftMenuComponent implements OnInit {
  readonly navigation = inject(NavigationService);
  private readonly router = inject(Router);
  readonly stocksSection = inject(SectionService).stocks;
  private readonly categories = inject(CategoryService);
  private readonly analyze = inject(AnalyzeService);
  private readonly destroyRef = inject(DestroyRef);
  readonly latestDrawBadge = signal('Kỳ mới nhất');

  ngOnInit(): void {
    this.router.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((event) => {
      if (event instanceof NavigationEnd) this.navigation.close();
    });
    combineLatest([
      this.categories.category$,
      this.router.events.pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        map((event) => event.urlAfterRedirects),
        startWith(this.router.url),
        map((url) => /^\/(?:analyze|entry|ai-lab)(?:\/|\?|#|$)/.test(url)),
        distinctUntilChanged(),
      ),
    ])
      .pipe(
        distinctUntilChanged(),
        switchMap(([category, needsBadge]) =>
          needsBadge
            ? this.analyze.getLatestDraw(category).pipe(catchError(() => of(null)))
            : of(null),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        const latest = result;
        const numbers = latest?.numbers ?? [];
        const summary = numbers.length
          ? numbers.length + ' số [' + numbers[0] + '..' + numbers[numbers.length - 1] + ']'
          : 'Chưa có kết quả';
        this.latestDrawBadge.set(
          latest?.specialNumber ? summary + ' + Phụ ' + latest.specialNumber : summary,
        );
      });
  }
  @HostListener('document:keydown', ['$event'])
  trapFocus(event: KeyboardEvent) {
    if (!this.navigation.open()) return;
    if (event.key === 'Escape') {
      this.navigation.close();
      return;
    }
    if (event.key !== 'Tab') return;
    const nodes = Array.from(
      document.querySelectorAll<HTMLElement>('#primary-navigation a, #primary-navigation button'),
    );
    const first = nodes[0],
      last = nodes[nodes.length - 1];
    if (
      event.shiftKey &&
      (document.activeElement === first || !nodes.includes(document.activeElement as HTMLElement))
    ) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }
  @HostListener('window:resize')
  closeDesktopDrawer() {
    if (window.innerWidth >= 768) this.navigation.close();
  }
}
