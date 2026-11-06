import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StockPositionInput } from '../../core/models/stock.model';

@Component({
  selector: 'app-stock-position-form',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stock-position-form.component.html',
  styleUrl: './stock-position-form.component.css',
})
export class StockPositionFormComponent {
  readonly value = input.required<StockPositionInput>();
  readonly busy = input(false);
  readonly changed = output<void>();
  readonly calculate = output<void>();
  readonly restart = output<void>();
  locked() {
    return !!this.value().plan?.executions.length;
  }
  edit() {
    this.changed.emit();
  }
  fillLimits() {
    const v = this.value(),
      policy = v.plan?.policy;
    if (!policy || !v.purchasePrice || !v.quantity) return;
    const capital = v.purchasePrice * v.quantity;
    policy.maxCapital = capital + (policy.additionalBudget || 0);
    policy.maxLossVnd = (capital * (v.maxLossPercent ?? 10)) / 100;
    this.edit();
  }
}
