import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { StockPositionInput, StockPositionResult } from '../../core/models/stock.model';
@Component({
  selector: 'app-stock-position-summary',
  standalone: true,
  imports: [DecimalPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let p = result(); @let plan = p?.positionPlan;
    <div class="kpis" aria-label="Tổng quan vị thế">
      <div>
        <span>GIÁ VỐN BÌNH QUÂN</span
        ><strong>{{
          (plan?.averageCost ?? position().purchasePrice) === null
            ? '—'
            : (plan?.averageCost ?? position().purchasePrice | number: '1.0-0')
        }}</strong
        ><small>VND / cổ phiếu</small>
      </div>
      <div>
        <span>GIÁ THAM CHIẾU</span
        ><strong>{{ p?.price == null ? '—' : (p!.price | number: '1.0-0') }}</strong
        ><small>{{
          plan
            ? plan.valuationClosed
              ? 'Nến đã đóng'
              : 'Nến đang hình thành'
            : 'Theo snapshot đang xem'
        }}</small>
      </div>
      <div [class.positive]="(p?.grossPnl ?? 0) > 0" [class.negative]="(p?.grossPnl ?? 0) < 0">
        <span>LÃI / LỖ VỊ THẾ CÒN LẠI</span
        ><strong>{{ p?.grossPnl == null ? '—' : (p!.grossPnl | number: '1.0-0') }}</strong
        ><small
          >{{
            p?.changePercent == null
              ? 'Chưa có nhận định'
              : (p!.changePercent | number: '1.2-2') + '%'
          }}
          · trước phí/thuế</small
        >
      </div>
      <div>
        <span>VỐN ĐÃ ĐƯA VÀO KẾ HOẠCH</span
        ><strong>{{ plan ? (plan.capitalCommitted | number: '1.0-0') : '—' }}</strong
        ><small>{{
          plan ? (plan.quantity | number: '1.0-0') + ' cổ phiếu còn giữ' : 'Nhập vị thế để bắt đầu'
        }}</small>
      </div>
    </div>
    @if (plan) {
      <p class="asof">
        Giá tính P&L: {{ plan.valuationAsOf * 1000 | date: 'dd/MM HH:mm' : '+0700' }} (VN) ·
        {{ updating() ? 'Đang cập nhật kế hoạch…' : 'Giữ hạn mức theo kế hoạch gốc' }}
      </p>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .kpis {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 14px;
      }
      .kpis > div {
        background: linear-gradient(145deg, #17283e, #111c30);
        border: 1px solid #2a3c58;
        border-radius: 16px;
        padding: 20px;
        min-width: 0;
      }
      .kpis > div:first-child {
        background: linear-gradient(145deg, #1f2850, #141d34);
        border-color: #514778;
      }
      span {
        display: block;
        color: #a6b6ce;
        font-size: 10px;
        font-weight: 700;
        letter-spacing: 1px;
      }
      strong {
        display: block;
        font-size: clamp(21px, 2.2vw, 30px);
        font-variant-numeric: tabular-nums;
        margin: 12px 0 5px;
        overflow-wrap: anywhere;
        color: #e6eef8;
      }
      small,
      .asof {
        font-size: 11px;
        color: #a6b6ce;
        line-height: 1.6;
      }
      .positive strong {
        color: #2dd4bf;
      }
      .negative strong {
        color: #fb7185;
      }
      .asof {
        text-align: right;
        margin: 9px 0 20px;
      }
      @media (max-width: 850px) {
        .kpis {
          grid-template-columns: repeat(2, minmax(0, 1fr));
        }
      }
      @media (max-width: 440px) {
        .kpis {
          gap: 8px;
        }
        .kpis > div {
          padding: 14px;
        }
        strong {
          font-size: 22px;
        }
      }
    `,
  ],
})
export class StockPositionSummaryComponent {
  readonly position = input.required<StockPositionInput>();
  readonly result = input<StockPositionResult | null>(null);
  readonly updating = input(false);
}
