import { TestBed } from '@angular/core/testing';
import { StockChartComponent } from './stock-chart.component';
import { stockChartCandles } from './stock-chart-data';

describe('Stock chart with the real chart library', () => {
  it('projects into the right margin without adding fake candles and clears layers for another short history', async () => {
    const text = spyOn(CanvasRenderingContext2D.prototype, 'fillText').and.callThrough();
    const fixture = TestBed.createComponent(StockChartComponent);
    fixture.nativeElement.style.width = '900px';
    const bars = Array.from({ length: 60 }, (_, i) => ({
      time: 1700000000 + i * 86400,
      open: 100 + i * 2,
      high: 103 + i * 2,
      low: 99 + i * 2,
      close: 102 + i * 2,
      volume: 1000,
      closed: true,
    }));
    fixture.componentRef.setInput('candles', bars);
    fixture.detectChanges();
    expect(fixture.componentInstance.projection()).not.toBeNull();
    expect(fixture.componentInstance['candlesSeries']!.data().length).toBe(60);
    fixture.componentInstance.toggleProjection('channel');
    fixture.componentInstance.toggleProjection('levels');
    for (let i = 0; i < 5; i++)
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
    expect(
      fixture.componentInstance['chart']!.timeScale().getVisibleLogicalRange()!.to,
    ).toBeGreaterThan(75);
    const chart = fixture.componentInstance['chart']!,
      futureX = Math.ceil(chart.timeScale().logicalToCoordinate(60 as any)!);
    const boundaryLabel = text.calls
      .allArgs()
      .filter((args) => args[0] === 'KỊCH BẢN')
      .at(-1)!;
    expect(boundaryLabel).toBeDefined();
    expect(boundaryLabel[1]).toBeGreaterThan(futureX - 10);
    expect(boundaryLabel[1]).toBeLessThan(futureX + 20);
    const picture = chart.takeScreenshot(),
      pixels = picture
        .getContext('2d')!
        .getImageData(
          futureX,
          20,
          Math.max(1, picture.width - futureX - 90),
          Math.floor(picture.height * 0.7),
        ).data;
    let drawn = 0;
    for (let i = 0; i < pixels.length; i += 4)
      if ((pixels[i] > 130 && pixels[i + 2] > 160) || (pixels[i + 1] > 130 && pixels[i + 2] > 170))
        drawn++;
    expect(drawn)
      .withContext('Fan/channel lines must paint visible pixels in the future margin')
      .toBeGreaterThan(25);
    expect(fixture.componentInstance['candlesSeries']!.data().length).toBe(60);
    fixture.componentInstance.toggleProjection('fan');
    expect(fixture.componentInstance.fanVisible()).toBeFalse();
    fixture.componentRef.setInput('candles', bars.slice(0, 5));
    fixture.detectChanges();
    expect(fixture.componentInstance.projection()).toBeNull();
    expect(fixture.componentInstance.renderError()).toBe('');
    fixture.destroy();
  });
  it('replaces position price lines and removes them when the layer is hidden', () => {
    const fixture = TestBed.createComponent(StockChartComponent);
    fixture.nativeElement.style.width = '900px';
    fixture.componentRef.setInput('candles', [
      { time: 1793671200, open: 29, high: 31, low: 28, close: 30, volume: 1000, closed: true },
    ]);
    fixture.componentRef.setInput('planLevels', [
      { price: 25, title: 'Dừng rủi ro', color: '#fb7185' },
      { price: 33, title: 'TP1', color: '#2dd4bf' },
    ]);
    fixture.detectChanges();
    expect(fixture.componentInstance['planLines'].length).toBe(2);
    fixture.componentRef.setInput('planLevels', [
      { price: 26, title: 'Dừng rủi ro', color: '#fb7185' },
    ]);
    fixture.detectChanges();
    expect(fixture.componentInstance['planLines'].length).toBe(1);
    fixture.componentInstance.togglePlan();
    expect(fixture.componentInstance['planLines'].length).toBe(0);
    expect(fixture.componentInstance.renderError()).toBe('');
    fixture.destroy();
  });
  it('paints actual candle and EMA pixels for a multi-year daily history, not only axes', async () => {
    const fixture = TestBed.createComponent(StockChartComponent);
    fixture.nativeElement.style.width = '900px';
    const start = Date.parse('2022-01-01T02:00:00Z') / 1000;
    const bars = Array.from({ length: 1000 }, (_, i) => {
      const open = 100000 + i * 120 + Math.sin(i) * 1200;
      return {
        time: start + i * 86400,
        open,
        high: open + 3000,
        low: open - 2000,
        close: open + 1500,
        volume: 1000000 + i * 100,
        closed: true,
      };
    });
    // Real provider failure: two distinct timestamps collapse to the same daily chart time.
    bars.splice(400, 0, { ...bars[400], time: bars[400].time - 7200, close: bars[400].open + 500 });
    fixture.componentRef.setInput('candles', bars);
    fixture.detectChanges();
    for (let i = 0; i < 5; i++)
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
    const picture = fixture.componentInstance['chart']!.takeScreenshot();
    const pixels = picture
      .getContext('2d')!
      .getImageData(0, 0, Math.floor(picture.width * 0.8), Math.floor(picture.height * 0.75)).data;
    let marks = 0;
    for (let i = 0; i < pixels.length; i += 4)
      if (
        (pixels[i + 1] > pixels[i] + 25 && pixels[i + 1] > pixels[i + 2] + 10) ||
        (pixels[i + 2] > pixels[i] + 35 && pixels[i + 2] > pixels[i + 1] + 25)
      )
        marks++;
    expect(marks)
      .withContext('Candlesticks and EMA must have visible colored pixels inside the price pane')
      .toBeGreaterThan(150);
    expect(fixture.componentInstance.renderError()).toBe('');
    expect(fixture.componentInstance['candlesSeries']!.data().length).toBe(1000);
    fixture.destroy();
  });
  it('keeps the later actual VIC daily snapshot and does not add volumes', () => {
    const early = {
      time: 1672099200,
      open: 26450,
      high: 26500,
      low: 26150,
      close: 26450,
      volume: 317300,
      closed: true,
    };
    const later = {
      time: 1672106400,
      open: 26450,
      high: 27400,
      low: 26050,
      close: 26700,
      volume: 1844000,
      closed: true,
    };
    expect(stockChartCandles([later, early], false)).toEqual([later]);
    expect(stockChartCandles([later, early], true)).toEqual([early, later]);
  });
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
