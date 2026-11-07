import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnChanges,
  inject,
  input,
  signal,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, finalize, switchMap, takeUntil } from 'rxjs';
import { LotteryCategory } from '@app/core/models/lottery-category.model';
import { BenchmarkReport, EvidenceInventory } from '@app/core/models/prediction-research.model';
import { PredictionResearchApiService } from '@app/core/services/prediction-research-api.service';

@Component({
  selector: 'app-research-panel',
  imports: [DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './research-panel.component.html',
  styleUrl: './research-panel.component.css',
})
export class ResearchPanelComponent implements OnChanges {
  readonly category = input.required<LotteryCategory>();
  readonly inventory = signal<EvidenceInventory | null>(null);
  readonly report = signal<BenchmarkReport | null>(null);
  readonly loading = signal(false);
  readonly running = signal(false);
  readonly error = signal('');
  readonly studyError = signal('');
  private readonly api = inject(PredictionResearchApiService);
  private readonly destroy = inject(DestroyRef);
  private readonly queries = new Subject<LotteryCategory>();
  private readonly cancel = new Subject<void>();
  constructor() {
    this.queries
      .pipe(
        switchMap((category) => {
          this.loading.set(true);
          this.error.set('');
          return this.api.inventory(category).pipe(
            catchError(() => {
              this.error.set('Chưa tải được kiểm kê dữ liệu. Bạn có thể tải lại.');
              return EMPTY;
            }),
            finalize(() => this.loading.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((value) => this.inventory.set(value));
  }
  ngOnChanges() {
    this.cancel.next();
    this.inventory.set(null);
    this.report.set(null);
    this.studyError.set('');
    this.load();
  }
  load() {
    this.queries.next(this.category());
  }
  run() {
    if (this.running()) return;
    this.running.set(true);
    this.studyError.set('');
    this.report.set(null);
    this.api
      .benchmark(this.category())
      .pipe(
        takeUntil(this.cancel),
        takeUntilDestroyed(this.destroy),
        finalize(() => this.running.set(false)),
      )
      .subscribe({
        next: (result) => {
          if (result.status === 'BUSY')
            this.studyError.set('Một benchmark đang chạy. Hãy thử lại sau.');
          else this.report.set(result);
        },
        error: () =>
          this.studyError.set('Chưa hoàn tất benchmark. Dữ liệu dự đoán đã lưu được giữ nguyên.'),
      });
  }
  download() {
    if (!this.report()) return;
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(this.report(), null, 2)], { type: 'application/json' }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = `vietlott-benchmark-${this.category()}.json`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url));
  }
  modelName(id: string) {
    return (
      (
        {
          uniform: 'Ngẫu nhiên đều',
          frequency: 'Tần suất',
          shrunk_frequency: 'Tần suất làm trơn',
          logistic_default: 'Logistic mặc định',
          logistic_selected: 'Logistic chọn trên validation',
          logistic_calibrated: 'Logistic hiệu chuẩn',
          logistic_feedback_simulated: 'Logistic + feedback mô phỏng',
          logistic_rank_portfolio: 'Logistic + nhóm ít trùng',
          logistic_coverage: 'Logistic + độ phủ',
          temporal_learning: 'Liên kết giữa các kỳ',
          bayesian_graph: 'Bayes & cặp số',
          deep_stacking: 'Heuristic kết hợp',
        } as Record<string, string>
      )[id] ?? id
    );
  }
}
