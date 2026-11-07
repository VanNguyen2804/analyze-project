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
}
