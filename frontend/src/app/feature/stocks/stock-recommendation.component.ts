import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { StockRecommendation } from '../../core/models/stock.model';

@Component({
  selector: 'app-stock-recommendation',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './stock-recommendation.component.html',
  styleUrl: './stock-recommendation.component.css',
})
export class StockRecommendationComponent {
  readonly recommendation = input.required<StockRecommendation>();
  unit() {
    return this.recommendation().unit === 'VND'
      ? 'VND/cổ phiếu'
      : this.recommendation().unit === 'THOUSAND_VND'
        ? 'nghìn VND/cổ phiếu'
        : 'đơn vị nguồn';
  }
}
