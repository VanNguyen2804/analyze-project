import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { StockCandleScenarios } from '../../core/models/stock.model';
@Component({
  selector: 'app-stock-candle-scenarios',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stock-candle-scenarios.component.html',
  styleUrl: './stock-candle-scenarios.component.css',
})
export class StockCandleScenariosComponent {
  readonly scenarios = input.required<StockCandleScenarios>();
  readonly stale = input(false);
  status(value: string) {
    return value === 'CONDITION_MET'
      ? 'Nến đạt điều kiện'
      : value === 'NO_QUANTITY'
        ? 'Không phân bổ lượng'
        : 'Chờ nến xác nhận';
  }
}
