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
    expect(fixture.componentInstance.renderError()).toBe('');
    fixture.componentRef.setInput('intraday', true);
    fixture.componentRef.setInput('purchasePrice', null);
    fixture.detectChanges();
    expect(fixture.componentInstance.renderError()).toBe('');
    fixture.destroy();
    expect(fixture.componentRef.hostView.destroyed).toBeTrue();
  });
  it('initializes after a hidden panel becomes visible and resizes to the actual container', async () => {
    const fixture = TestBed.createComponent(StockChartComponent);
    const root: HTMLElement = fixture.nativeElement;
    root.style.display = 'none';
    root.style.width = '900px';
    fixture.componentRef.setInput('candles', [
      {
        time: Date.parse('2026-11-03T02:00:00Z') / 1000,
        open: 230000,
        high: 235000,
        low: 229000,
        close: 234000,
        volume: 1000,
        closed: true,
      },
    ]);
    fixture.detectChanges();
    expect(root.querySelectorAll('canvas').length).toBe(0);
    root.style.display = 'block';
    for (let frame = 0; frame < 20 && !root.querySelector('canvas'); frame++) {
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
    }
    expect(root.querySelectorAll('canvas').length).toBeGreaterThan(0);
    const chart = root.querySelector('.chart') as HTMLElement;
    expect(chart.clientWidth).toBe(900);
    const canvas = root.querySelector('canvas') as HTMLCanvasElement;
    expect(canvas.getBoundingClientRect().width).toBeGreaterThan(0);
    expect(fixture.componentInstance.renderError()).toBe('');
    fixture.destroy();
  });
});
