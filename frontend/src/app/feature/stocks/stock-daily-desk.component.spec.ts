import { TestBed } from '@angular/core/testing';
import { StockDailyDeskComponent } from './stock-daily-desk.component';
import { StockDailyAssessment } from '../../core/models/stock.model';

function day(date: string, price: number): StockDailyAssessment {
  return {
    date,
    asOf: Date.parse(date + 'T02:00:00Z') / 1000,
    price,
    changePercent: 1,
    bias: 'MIXED',
    summary: 'Các chỉ báo chưa đồng thuận; không phải xác suất thắng.',
    buyConfirmations: 2,
    sellConfirmations: 1,
    values: {
      rsi: 55,
      previousRsi: 54,
      ema20: 100,
      ema50: 98,
      ema200: null,
      tenkan: 101,
      kijun: 99,
      cloudA: 100,
      cloudB: 98,
      volumeAverage: 1000,
      volumeRatio: 1.1,
      rangeLow: 90,
      rangeHigh: 110,
      rangeLowDate: '2026-09-01',
      rangeHighDate: '2026-10-01',
      swingDirection: 'UP',
      fibonacci: [{ ratio: 0.382, price: 102.36 }],
    },
    readings: [
      {
        key: 'rsi',
        name: 'RSI14 · Wilder',
        current: '55 (trước 54)',
        buyTarget: 'RSI tăng trong 50–70',
        sellTarget: 'RSI giảm dưới 50',
        buyReady: true,
        sellReady: false,
        explanation: 'So với phiên trước.',
      },
      {
        key: 'ichimoku',
        name: 'Ichimoku',
        current: 'Trên mây',
        buyTarget: 'Đóng trên 101',
        sellTarget: 'Đóng dưới 98',
        buyReady: true,
        sellReady: false,
        explanation: 'Dịch 26 phiên.',
      },
    ],
    cautions: ['Tính lại từ dữ liệu tới ngày được chọn.'],
  };
}
describe('Daily technical desk', () => {
  it('shows actual values next to buy/sell conditions and switches to the selected historical assessment', () => {
    const fixture = TestBed.createComponent(StockDailyDeskComponent);
    fixture.componentRef.setInput('desk', {
      version: 'test',
      unit: 'VND',
      priceBasis: 'UNKNOWN',
      days: [day('2026-11-03', 234000), day('2026-11-02', 232000)],
    });
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.textContent).toContain('Điều kiện nghiêng mua');
    expect(root.textContent).toContain('55 (trước 54)');
    expect(root.textContent).toContain('Đóng dưới 98');
    const select = root.querySelector('select')!;
    select.value = '2026-11-02';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(fixture.componentInstance.selected()?.price).toBe(232000);
    expect(root.querySelector('.overview')?.textContent).toContain('232,000');
    fixture.destroy();
  });
  it('does not fabricate a review when no daily candles are available', () => {
    const fixture = TestBed.createComponent(StockDailyDeskComponent);
    fixture.componentRef.setInput('desk', {
      version: 'test',
      unit: 'VND',
      priceBasis: 'UNKNOWN',
      days: [],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Chưa đủ nến ngày');
    fixture.destroy();
  });
});
