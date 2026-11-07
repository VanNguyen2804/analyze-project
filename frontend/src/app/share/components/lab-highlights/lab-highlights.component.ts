import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { toObservable, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, combineLatest, filter, switchMap, tap, timer } from 'rxjs';
import { ForecastApiService } from '../../../core/services/forecast-api.service';
import { LabTicket } from '../../../core/models/forecast.model';
import { LotteryCategory } from '../../../core/models/lottery-category.model';

@Component({
  selector: 'app-lab-highlights',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './lab-highlights.component.html',
  styleUrl: './lab-highlights.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LabHighlightsComponent {
  readonly category = input<string>('MEGA');
  readonly tickets = input<LabTicket[]>([]);
  readonly remote = input(false);
  readonly loading = signal(false);
  readonly error = signal('');
  private readonly loaded = signal<LabTicket[]>([]);
  private readonly api = inject(ForecastApiService);
  private readonly destroy = inject(DestroyRef);
  readonly results = computed(() =>
    (this.remote() ? this.loaded() : this.tickets()).filter(
      (t) => (t.pastPerformance?.bestMatched ?? 0) >= 3,
    ),
  );
  readonly groups = computed(() => {
    const groups = new Map<
      string,
      {
        key: string;
        model: string;
        modelVersion?: string;
        targetDate: string;
        performance: NonNullable<LabTicket['pastPerformance']>;
        tickets: LabTicket[];
      }
    >();
    for (const ticket of this.results()) {
      const key = JSON.stringify([
        ticket.model,
        ticket.cohort ?? ticket.modelVersion ?? '',
        ticket.targetDate,
      ]);
      const group = groups.get(key);
      if (group) group.tickets.push(ticket);
      else
        groups.set(key, {
          key,
          model: ticket.model,
          modelVersion: ticket.modelVersion,
          targetDate: ticket.targetDate,
          performance: ticket.pastPerformance!,
          tickets: [ticket],
        });
    }
    return [...groups.values()];
  });
  constructor() {
    combineLatest([toObservable(this.category), toObservable(this.remote)])
      .pipe(
        tap(() => {
          this.loaded.set([]);
          this.error.set('');
          this.loading.set(false);
        }),
        switchMap(([category, remote]) =>
          remote
            ? timer(0, 60000).pipe(
                filter((tick) => tick === 0 || !document.hidden),
                switchMap(() => {
                  this.loading.set(true);
                  return this.api.labHighlights(category as LotteryCategory).pipe(
                    tap((rows) => {
                      this.loaded.set(rows);
                      this.loading.set(false);
                      this.error.set('');
                    }),
                    catchError(() => {
                      this.loading.set(false);
                      this.error.set('Chưa tải được dự đoán AI Lab. Hệ thống sẽ thử lại sau.');
                      return EMPTY;
                    }),
                  );
                }),
              )
            : EMPTY,
        ),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe();
  }
  name(model: string) {
    return (
      (
        {
          logistic: 'Logistic Regression (học máy)',
          logistic_no_feedback: 'Logistic không feedback',
          logistic_coverage: 'Logistic + ghép vé độ phủ (không feedback)',
          shrunk_frequency: 'Tần suất làm trơn (prior 16 kỳ)',
          recency: 'Tần suất suy giảm theo thời gian',
        } as Record<string, string>
      )[model] ?? model
    );
  }
}
