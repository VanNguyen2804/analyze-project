import { TestBed } from '@angular/core/testing';
import { StockRecommendationComponent } from './stock-recommendation.component';
import { StockRecommendation } from '../../core/models/stock.model';

describe('Plain language stock recommendation', () => {
  it('shows price triggers, holding-specific sell wording and explicit confirmation conditions', () => {
    const fixture = TestBed.createComponent(StockRecommendationComponent);
    const recommendation: StockRecommendation = {
      version: 'test',
      action: 'WATCH',
      title: 'Chờ giá xác nhận',
      summary: 'Chưa có nến đóng vượt kháng cự.',
      unit: 'VND',
      asOf: 1793671200,
      evaluatedPrice: 100000,
      buyAbove: 102000,
      sellBelow: 95000,
      stopLoss: 99000,
      takeProfit: 108000,
      volumeRatio: 0.9,
      volumeConfirmation: 1.2,
      buyConditions: ['Nến đóng trên mốc mua và khối lượng đạt 1.2 lần.'],
      sellConditions: ['Chỉ cân nhắc bán nếu đang nắm giữ.'],
      cautions: ['Kịch bản tham khảo, chưa xác nhận hành động.'],
    };
    fixture.componentRef.setInput('recommendation', recommendation);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.textContent).toContain('102,000');
    expect(root.textContent).toContain('95,000');
    expect(root.textContent).toContain('nếu đang nắm giữ');
    expect(root.textContent).toContain('VND/cổ phiếu');
    expect(root.textContent).toContain('cần ≥1.2 lần');
    expect(root.textContent).not.toContain('%');
    fixture.destroy();
  });
});
