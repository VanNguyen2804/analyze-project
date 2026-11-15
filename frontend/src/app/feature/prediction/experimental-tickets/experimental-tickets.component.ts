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
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, filter, switchMap, tap, timer } from 'rxjs';
import { ForecastApiService } from '../../../core/services/forecast-api.service';
import { LabTicket } from '../../../core/models/forecast.model';
import { LotteryCategory } from '../../../core/models/lottery-category.model';

@Component({
  selector: 'app-experimental-tickets',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './experimental-tickets.component.html',
  styleUrl: './experimental-tickets.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExperimentalTicketsComponent {
  readonly category = input<string>('MEGA');
  readonly rows = signal<LabTicket[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  private readonly api = inject(ForecastApiService);
  private readonly destroy = inject(DestroyRef);
  readonly groups = computed(() => {
    const groups = new Map<
      string,
      { key: string; model: string; date: string; tickets: LabTicket[] }
    >();
    for (const row of this.rows()) {
      const key = JSON.stringify([row.model, row.cohort, row.targetDate]);
      const group = groups.get(key);
      if (group) group.tickets.push(row);
      else groups.set(key, { key, model: row.model, date: row.targetDate, tickets: [row] });
    }
    return [...groups.values()];
  });
  constructor() {
    toObservable(this.category)
      .pipe(
        tap(() => {
          this.rows.set([]);
          this.error.set('');
        }),
        switchMap((category) =>
          timer(0, 60000).pipe(
            filter((tick) => tick === 0 || !document.hidden),
            switchMap(() => {
              this.loading.set(true);
              return this.api.labExperiments(category as LotteryCategory).pipe(
                tap((rows) => {
                  this.rows.set(rows);
                  this.loading.set(false);
                  this.error.set('');
                }),
                catchError(() => {
                  this.loading.set(false);
                  this.error.set('Chưa tải được vé thử nghiệm. Hệ thống sẽ thử lại.');
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
  name(model: string) {
    return model === 'logistic_concentrated' ? 'Ghép tập trung' : 'Kết hợp tập trung và phủ rộng';
  }
  async copy(ticket: LabTicket) {
    try {
      await navigator.clipboard.writeText(
        ticket.numbers.map((n) => String(n).padStart(2, '0')).join(' '),
      );
      this.error.set('Đã sao chép sáu số.');
    } catch {
      this.error.set('Không sao chép được. Bạn có thể chọn và sao chép dãy số trực tiếp.');
    }
  }
}
