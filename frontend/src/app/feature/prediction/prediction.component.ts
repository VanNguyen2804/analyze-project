import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, exhaustMap, filter, merge, switchMap, tap, timer } from 'rxjs';
import { CategoryService } from '../../core/services/category.service';
import { ForecastApiService } from '../../core/services/forecast-api.service';
import { LabPredictionPerformance, LabPredictions, LabTicketPriority, LabPredictionGroup } from '../../core/models/forecast.model';

@Component({
  selector: 'app-prediction',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './prediction.component.html',
  styleUrl: './prediction.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PredictionComponent {
  private readonly categories = inject(CategoryService);
  private readonly api = inject(ForecastApiService);
  private readonly destroy = inject(DestroyRef);
  private readonly reload = new Subject<void>();
  private readonly response = signal<LabPredictions | null>(null);
  private readonly now = signal(Date.now());
  readonly category = this.categories.category;
  readonly loading = signal(false);
  readonly error = signal('');
  readonly selectedPriority = signal<LabTicketPriority | null>(null);
  readonly selectedModel = signal('');
  prioritizedTickets(group: LabPredictionGroup) {
    return [...group.tickets].sort((a, b) =>
      (this.priority(group, a.id)?.rank ?? 99) - (this.priority(group, b.id)?.rank ?? 99));
  }
  priority(group: LabPredictionGroup, id: string) {
    return group.ticketPriorities?.find(item => item.ticketId === id);
  }
  priorityReason(item: LabTicketPriority) {
    const hits = this.level(item.performance, 3)?.drawHits ?? 0;
    const n = item.performance.evaluatedDraws;
    if (!n) return 'Chưa có kỳ đã đối soát để đánh giá dãy này.';
    return hits >= 2 ? 'Đã khớp ≥3 số lặp lại; thứ tự xét cả tỷ lệ và cỡ mẫu.'
      : hits === 1 ? 'Mới khớp ≥3 số một kỳ; cần thêm kết quả để đánh giá.'
      : 'Chưa ghi nhận kỳ khớp ≥3 số; ưu tiên thấp hơn dãy đã khớp.';
  }
  readonly copyMessage = signal('');
  readonly view = computed(() => {
    const value = this.response();
    return value?.category === this.category() ? value : null;
  });
  readonly groups = computed(() =>
    (this.view()?.groups ?? []).filter(
      (group) =>
        group.targetDate === this.view()?.targetDate &&
        Date.parse(`${group.targetDate}T18:00:00+07:00`) > this.now(),
    ),
  );
  readonly ticketCount = computed(() =>
    this.groups().reduce((sum, group) => sum + group.tickets.length, 0),
  );

  constructor() {
    toObservable(this.category)
      .pipe(
        tap(() => {
          this.response.set(null);
          this.error.set('');
          this.copyMessage.set('');
        }),
        switchMap((category) =>
          merge(timer(0, 60_000), this.reload).pipe(
            filter(() => !document.hidden),
            tap(() => this.now.set(Date.now())),
            exhaustMap(() => {
              this.loading.set(true);
              return this.api.labPredictions(category).pipe(
                tap((value) => {
                  if (value.category !== category) throw new Error('Loại xổ số không khớp.');
                  this.response.set(value);
                  this.now.set(Date.now());
                  this.error.set('');
                  this.loading.set(false);
                }),
                catchError(() => {
                  this.loading.set(false);
                  this.error.set('Chưa tải được dự đoán AI Lab. Hãy thử lại sau ít giây.');
                  return EMPTY;
                }),
              );
            }),
          ),
        ),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe();
  }

  selectCategory(category: string) {
    this.categories.setCategory(category);
  }
  refresh() {
    this.reload.next();
  }
  level(performance: LabPredictionPerformance, threshold: number) {
    return performance.levels.find((level) => level.threshold === threshold);
  }
  name(model: string) {
    return (
      (
        {
          logistic: 'Logistic Regression',
          logistic_no_feedback: 'Logistic không feedback',
          logistic_feedback_coverage: 'Logistic học phản hồi + độ phủ',
          logistic_coverage: 'Logistic + ghép vé độ phủ',
          shrunk_frequency: 'Tần suất làm trơn',
          recency: 'Tần suất suy giảm theo thời gian',
        } as Record<string, string>
      )[model] ?? model
    );
  }
  async copyTicket(numbers: number[]) {
    this.copyMessage.set('');
    try {
      await navigator.clipboard.writeText(
        numbers.map((number) => String(number).padStart(2, '0')).join(' - '),
      );
      this.copyMessage.set('Đã sao chép dãy số.');
    } catch {
      this.copyMessage.set('Chưa sao chép được. Vui lòng chọn và sao chép dãy số trực tiếp.');
    }
  }
}
