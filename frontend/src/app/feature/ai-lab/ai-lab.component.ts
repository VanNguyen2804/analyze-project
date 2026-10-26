import { ForecastApiService } from '../../core/services/forecast-api.service';
import { LotteryCategory } from '../../core/models/lottery-category.model';
import { LabOverview, LabTicket } from '../../core/models/forecast.model';
import {
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
  computed,
  ChangeDetectionStrategy,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, distinctUntilChanged, finalize, switchMap, timer } from 'rxjs';
import { AnalyzeService } from '../../core/services/analyze.service';
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-ai-lab',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './ai-lab.component.html',
  styleUrl: './ai-lab.component.css',
})
export class AiLabComponent implements OnInit {
  readonly category = signal<LotteryCategory>('MEGA');
  readonly page = signal(0);
  readonly data = signal<LabOverview | undefined>(undefined);
  readonly busy = signal(false);
  readonly mutating = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  private readonly destroy = inject(DestroyRef);

  private readonly analyze = inject(AnalyzeService);
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
          return this.api.labOverview(query.category, query.page).pipe(
            catchError(() => {
              this.error.set('Không tải được phòng thử nghiệm. Hãy thử tải lại.');
              return EMPTY;
            }),
            finalize(() => {
              this.busy.set(false);
            }),
          );
        }),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((data) => {
        this.data.set(data);
      });
    this.analyze.currentCategory$
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroy))
      .subscribe((category) => {
        this.category.set(category === 'POWER' ? 'POWER' : 'MEGA');
        this.page.set(0);
        this.data.set(undefined);
        this.load();
      });
    timer(60000, 60000)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(() => {
        if (!this.busy() && !this.mutating()) this.load();
      });
  }
  load() {
    this.requests.next({ category: this.category(), page: this.page() });
  }
  select(category: string) {
    if (!this.mutating()) this.analyze.setCategory(category);
  }
  changePage(delta: number) {
    if (!this.busy() && !this.mutating()) {
      this.page.update((value) => value + delta);
      this.load();
    }
  }
  run() {
    if (this.busy() || this.mutating()) return;
    this.mutating.set(true);
    this.error.set('');
    this.message.set('');
    this.api
      .runLab()
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => {
          this.mutating.set(false);
        }),
      )
      .subscribe({
        next: (result) => {
          this.message.set(
            result.status === 'BUSY'
              ? 'Tác vụ đang chạy, hãy chờ lượt cập nhật tiếp theo.'
              : 'Đã kiểm tra chốt kỳ tiếp theo và đối soát. Xem trạng thái lưu bên dưới.',
          );
          this.load();
        },
        error: () => this.error.set('Không hoàn tất thao tác. Hãy thử lại sau.'),
      });
  }
  name(model: string) {
    return model === 'logistic'
      ? 'Logistic Regression (học máy)'
      : 'Tần suất suy giảm theo thời gian';
  }
  percent(value: number | null | undefined) {
    return value == null ? 'Chưa có' : `${value.toFixed(1)}%`;
  }
  matched(row: LabTicket, number: number) {
    return (
      row.settlement?.status === 'SETTLED' && (row.settlement.actualNumbers ?? []).includes(number)
    );
  }
  readonly pages = computed(() => Math.max(1, Math.ceil((this.data()?.totalRows ?? 0) / 50)));
}
