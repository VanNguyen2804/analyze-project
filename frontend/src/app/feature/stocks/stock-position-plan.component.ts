import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StockPositionPlan, StockPlanStep } from '../../core/models/stock.model';
export interface StockExecutionDraft {
  stepId: string;
  price: number;
  quantity: number;
}
@Component({
  selector: 'app-stock-position-plan',
  standalone: true,
  imports: [DecimalPipe, DatePipe, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stock-position-plan.component.html',
  styleUrl: './stock-position-plan.component.css',
})
export class StockPositionPlanComponent {
  readonly plan = input<StockPositionPlan | null>(null);
  readonly busy = input(false);
  readonly recorded = output<StockExecutionDraft>();
  readonly editing = signal<string | null>(null);
  actualPrice: number | null = null;
  actualQuantity: number | null = null;
  hasAveraging() {
    return this.plan()?.steps.some((s) => s.side === 'BUY') ?? false;
  }
  status(value: string) {
    return (
      (
        {
          READY: 'Đủ điều kiện',
          WAITING: 'Chờ xác nhận',
          WAIT_PREVIOUS: 'Sau bước trước',
          BLOCKED: 'Tạm dừng',
          DONE: 'Đã ghi nhận',
          NEED_SELLABLE: 'Cần lượng có thể bán',
        } as Record<string, string>
      )[value] || value
    );
  }
  title(value: string) {
    return (
      (
        {
          REFERENCE: 'Kịch bản tham khảo',
          RISK: 'Ưu tiên giảm rủi ro',
          TAKE_PROFIT: 'Đến mốc chốt lời',
          AVERAGE: 'Bình giá có điều kiện',
          REVIEW: 'Đánh giá lại vị thế',
          HOLD: 'Theo dõi kế hoạch',
          CLOSED: 'Đã đóng vị thế',
        } as Record<string, string>
      )[value] || value
    );
  }
  canRecord(step: StockPlanStep) {
    return (
      !this.busy() &&
      step.quantity > 0 &&
      !['DONE', 'BLOCKED', 'WAIT_PREVIOUS', 'NEED_SELLABLE'].includes(step.status)
    );
  }
  open(step: StockPlanStep) {
    this.actualPrice = null;
    this.actualQuantity = null;
    this.editing.set(step.id);
  }
  submit(step: StockPlanStep) {
    if (
      !this.canRecord(step) ||
      !this.actualPrice ||
      !Number.isFinite(this.actualPrice) ||
      this.actualPrice <= 0 ||
      !this.actualQuantity ||
      this.actualQuantity <= 0 ||
      !Number.isInteger(this.actualQuantity) ||
      this.actualQuantity > step.quantity
    )
      return;
    this.recorded.emit({ stepId: step.id, price: this.actualPrice, quantity: this.actualQuantity });
    this.editing.set(null);
  }
}
