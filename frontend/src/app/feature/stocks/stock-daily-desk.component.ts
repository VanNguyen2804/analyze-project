import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { StockDailyAssessment, StockDailyDesk } from '../../core/models/stock.model';

@Component({
  selector: 'app-stock-daily-desk',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, PercentPipe],
  templateUrl: './stock-daily-desk.component.html',
  styleUrl: './stock-daily-desk.component.css',
})
export class StockDailyDeskComponent {
  readonly desk = input.required<StockDailyDesk>();
  readonly selectedDate = signal('');
  readonly selected = computed<StockDailyAssessment | undefined>(
    () => this.desk().days.find((d) => d.date === this.selectedDate()) ?? this.desk().days[0],
  );
  unit() {
    return this.desk().unit === 'VND'
      ? 'VND/cổ phiếu'
      : this.desk().unit === 'THOUSAND_VND'
        ? 'nghìn VND/cổ phiếu'
        : 'đơn vị nguồn';
  }
}
