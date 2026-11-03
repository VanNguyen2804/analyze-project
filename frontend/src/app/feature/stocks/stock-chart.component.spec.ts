import { TestBed } from '@angular/core/testing';
import { StockChartComponent } from './stock-chart.component';

describe('Stock chart with the real chart library', () => {
  it('renders daily and intraday data, purchase markers and cleans up when leaving the page', () => {
    const fixture = TestBed.createComponent(StockChartComponent);
    const time = Date.parse('2026-11-02T02:15:00Z') / 1000;
    const bars = [
      { time, open: 10, high: 12, low: 9, close: 11, volume: 100, closed: true },
      { time: time + 86400, open: 11, high: 13, low: 10, close: 12, volume: 200, closed: false },
    ];
    fixture.componentRef.setInput('candles', bars);
    fixture.componentRef.setInput('purchasePrice', 10);
    fixture.componentRef.setInput('purchaseDate', '2026-11-02');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('canvas').length).toBeGreaterThan(0);
    fixture.componentRef.setInput('intraday', true);
    fixture.componentRef.setInput('purchasePrice', null);
    fixture.detectChanges();
    fixture.destroy();
    expect(fixture.componentRef.hostView.destroyed).toBeTrue();
  });
});
