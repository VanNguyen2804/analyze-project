import { TestBed } from '@angular/core/testing';
import { StockCandleScenariosComponent } from './stock-candle-scenarios.component';
import { StockCandleScenarios } from '../../core/models/stock.model';
const scenarios: StockCandleScenarios = {
  version: 'test',
  snapshotId: 'fixture',
  status: 'MONITOR',
  summary: 'Theo dõi điều kiện nến đóng',
  confirmed: true,
  asOf: 1793952000,
  referencePrice: 50000,
  averageCost: 48000,
  holdingQuantity: 200,
  stopPrice: 46000,
  methodology: ['Hai cách mua là phương án thay thế'],
  cautions: ['Lượng đang giữ không đồng nghĩa lượng có thể bán'],
  items: [
    {
      id: 'BUY_BREAKOUT',
      side: 'BUY',
      title: 'Mua thêm khi vượt kháng cự',
      status: 'CONDITION_MET',
      triggerPrice: 51000,
      suggestedQuantity: 50,
      additionalCash: 2550000,
      averageAfter: 48600,
      quantityAfter: 250,
      estimatedPnlAtTrigger: 600000,
      conditions: [{ label: 'Nến đóng vượt mốc mua', met: true }],
      blockers: [],
    },
  ],
};
describe('Candle-based position scenarios', () => {
  it('shows conditions, funding required and no execution input; stale results cannot display confirmation', () => {
    const fixture = TestBed.createComponent(StockCandleScenariosComponent);
    fixture.componentRef.setInput('scenarios', scenarios);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Vốn cần bổ sung');
    expect(fixture.nativeElement.querySelectorAll('input, form').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('article.met').length).toBe(1);
    fixture.componentRef.setInput('stale', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('article.met').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('li.confirmed').length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Chờ cập nhật');
    fixture.destroy();
  });
});
