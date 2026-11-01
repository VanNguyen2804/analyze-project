import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, finalize, switchMap, timer } from 'rxjs';
import { MegaPortfolioOverview } from '@app/core/models/mega-portfolio.model';
import { MegaPortfolioApiService } from '@app/core/services/mega-portfolio-api.service';

@Component({
  selector: 'app-mega-portfolio',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './mega-portfolio.component.html',
  styleUrl: './mega-portfolio.component.css',
})
export class MegaPortfolioComponent implements OnInit {
  private readonly api = inject(MegaPortfolioApiService);
  private readonly destroy = inject(DestroyRef);
  private readonly requests = new Subject<number>();
  readonly copied = output<number[]>();
  readonly data = signal<MegaPortfolioOverview | null>(null);
  readonly busy = signal(false);
  readonly mutating = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly page = signal(0);
  readonly pages = computed(() => Math.max(1, Math.ceil((this.data()?.totalRows ?? 0) / 10)));
  readonly trial = computed(() => this.data()?.current?.arms.find((arm) => arm.id === 'portfolio'));

  ngOnInit() {
    this.requests
      .pipe(
        switchMap((page) => {
          this.busy.set(true);
          this.error.set('');
          return this.api.overview(page).pipe(
            catchError(() => {
              this.error.set('Chưa tải được thử nghiệm MEGA. Hãy thử tải lại.');
              return EMPTY;
            }),
            finalize(() => this.busy.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((data) => this.data.set(data));
    this.load();
    timer(60000, 60000)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(() => {
        if (!this.busy() && !this.mutating()) this.load();
      });
  }

  load() {
    this.requests.next(this.page());
  }

  changePage(delta: number) {
    const next = this.page() + delta;
    if (this.busy() || this.mutating() || next < 0 || next >= this.pages()) return;
    this.page.set(next);
    this.load();
  }

  run() {
    if (this.busy() || this.mutating()) return;
    this.mutating.set(true);
    this.error.set('');
    this.message.set('');
    this.api
      .run()
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.mutating.set(false)),
      )
      .subscribe({
        next: (result) => {
          this.message.set(this.statusName(result.status));
          this.load();
        },
        error: () => this.error.set('Chưa hoàn tất chốt và đối soát. Hãy thử lại.'),
      });
  }

  statusName(status: string) {
    return (
      (
        {
          NOT_RUN: 'Đang chờ lượt chốt tự động đầu tiên.',
          SAVED: 'Đã chốt bộ vé mới và kiểm tra đối soát.',
          ALREADY_SAVED: 'Giữ nguyên bộ vé đã chốt; đã kiểm tra đối soát.',
          NOT_SAVED: 'Chưa đủ dữ liệu hoặc chưa chốt được kỳ mới. Hệ thống sẽ thử lại.',
          DISABLED: 'Tự chốt đang tắt; vẫn theo dõi các bộ đã lưu.',
          BUSY: 'Đang có lượt xử lý khác. Hãy chờ cập nhật.',
          ERROR: 'Lượt xử lý chưa hoàn tất. Hệ thống sẽ thử lại.',
          PENDING: 'Chờ kết quả',
          SETTLED: 'Đã đối soát',
          RESULT_UNAVAILABLE: 'Kết quả cần xác minh lại',
        } as Record<string, string>
      )[status] ?? 'Đang theo dõi'
    );
  }

  format(number: number) {
    return String(number).padStart(2, '0');
  }
}
