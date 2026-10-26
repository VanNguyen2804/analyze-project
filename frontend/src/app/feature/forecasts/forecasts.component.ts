import { ForecastApiService } from '../../core/services/forecast-api.service';
import { LotteryCategory } from '../../core/models/lottery-category.model';
import { ForecastOverview, SavedForecast } from '../../core/models/forecast.model';
import {
  Component,
  OnInit,
  DestroyRef,
  inject,
  signal,
  computed,
  ChangeDetectionStrategy,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EMPTY, Subject, switchMap, catchError, finalize, tap, distinctUntilChanged } from 'rxjs';
import { RouterLink } from '@angular/router';
import { AnalyzeService } from '../../core/services/analyze.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-forecasts',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './forecasts.component.html',
  styleUrl: './forecasts.component.css',
})
export class ForecastsComponent implements OnInit {
  private readonly analyze = inject(AnalyzeService);
  readonly category = signal<LotteryCategory>('MEGA');
  readonly busy = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly ledger = signal<ForecastOverview | undefined>(undefined);
  readonly groups = computed(() => this.ledger()?.evaluation.groups ?? []);
  readonly rows = computed(() => this.ledger()?.rows ?? []);
  readonly page = signal(0);
  readonly totalRows = computed(() => this.ledger()?.totalRows ?? 0);
  readonly mutating = signal(false);
  readonly statusFilter = signal('ALL');
  private loadedCategory = '';
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<{
    category: LotteryCategory;
    page: number;
  }>();
  private readonly api = inject(ForecastApiService);
  ngOnInit() {
    this.requests
      .pipe(
        switchMap((query) => {
          this.busy.set(true);
          this.error.set('');
          return this.api.overview(query.category, query.page).pipe(
            tap((payload) => {
              this.ledger.set(payload);

              this.loadedCategory = query.category;
            }),
            catchError((error) => {
              this.error.set(
                error.status === 503
                  ? 'Máy chủ đang quá tải. Vui lòng chờ rồi tải lại.'
                  : 'Không tải được dữ liệu dự đoán. Kiểm tra kết nối và backend.',
              );
              return EMPTY;
            }),
            finalize(() => {
              this.busy.set(false);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
    this.analyze.currentCategory$
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((category) => {
        this.category.set(category === 'POWER' ? 'POWER' : 'MEGA');
        this.statusFilter.set('ALL');
        if (this.category() !== this.loadedCategory) {
          this.ledger.set(undefined);
        }
        this.load();
      });
  }
  categoryChanged() {
    this.analyze.setCategory(this.category());
  }
  readonly evaluatedCount = computed(() =>
    this.groups().reduce((sum, g) => sum + (g.evaluatedDraws ?? 0), 0),
  );
  readonly pendingCount = computed(() =>
    this.groups().reduce((sum, g) => sum + (g.pendingOrUnavailable ?? 0), 0),
  );
  readonly hit3Count = computed(() =>
    this.groups().reduce(
      (sum, g) =>
        sum + (g.matchHistogram ?? []).slice(3).reduce((a: number, b: number) => a + b, 0),
      0,
    ),
  );
  readonly pageCount = computed(() => Math.max(1, Math.ceil(this.totalRows() / 50)));
  readonly filteredRows = computed(() =>
    this.statusFilter() === 'ALL'
      ? this.rows()
      : this.rows().filter((row) => (row.settlement?.status ?? 'PENDING') === this.statusFilter()),
  );
  algorithmName(id: string): string {
    return (
      (
        {
          temporal_learning: 'Học liên kết giữa các kỳ',
          bayesian_graph: 'Bayes & liên kết cặp số',
          deep_stacking: 'Tổ hợp tần suất & độ trễ',
        } as Record<string, string>
      )[id] ?? id
    );
  }
  isMatched(row: SavedForecast, number: number): boolean {
    return (
      row.settlement?.status === 'SETTLED' && (row.settlement.actualNumbers ?? []).includes(number)
    );
  }
  metric(value: number | null | undefined) {
    return value == null ? 'N/A' : value.toFixed(3);
  }
  status(value?: string) {
    return value === 'SETTLED'
      ? 'Đã đối soát'
      : value === 'RESULT_UNAVAILABLE'
        ? 'Cần xác minh lại'
        : 'Chờ kết quả';
  }
  load(resetPage = true) {
    if (this.mutating()) return;
    if (this.category() !== this.loadedCategory) {
      this.ledger.set(undefined);
    }
    if (resetPage) this.page.set(0);
    this.requests.next({ category: this.category(), page: this.page() });
  }
  changePage(delta: number) {
    if (!this.busy()) {
      this.page.update((value) => value + delta);
      this.load(false);
    }
  }
  reconcile() {
    this.mutate('reconcile');
  }
  capture() {
    this.mutate('capture');
  }
  private mutate(action: 'capture' | 'reconcile') {
    if (this.busy()) return;
    this.mutating.set(true);
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    this.api
      .mutate(action)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.mutating.set(false);
          this.message.set(
            result.status === 'BUSY'
              ? (result.message ?? '?ang x? l?, vui l?ng ch?.')
              : action === 'capture'
                ? 'Đã kiểm tra lưu kỳ tiếp theo. Xem các bộ số đã chốt bên dưới; bản đầu tiên luôn được giữ nguyên.'
                : 'Đã đối soát kết quả hiện có.',
          );
          this.load();
        },
        error: () => {
          this.mutating.set(false);
          this.error.set('Không hoàn tất thao tác. Vui lòng chờ rồi thử lại.');
          this.busy.set(false);
        },
      });
  }
}
