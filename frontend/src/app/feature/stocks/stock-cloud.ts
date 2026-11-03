import {
  IChartApi,
  ISeriesApi,
  ISeriesPrimitive,
  IPrimitivePaneView,
  Time,
} from 'lightweight-charts';
import { CanvasRenderingTarget2D } from 'fancy-canvas';

export class StockCloud implements ISeriesPrimitive<Time> {
  private points: { time: Time; a: number; b: number }[] = [];
  constructor(
    private chart: IChartApi,
    private series: ISeriesApi<'Candlestick'>,
  ) {}
  setData(points: { time: Time; a: number; b: number }[]) {
    this.points = points;
  }
  paneViews(): readonly IPrimitivePaneView[] {
    return [{ zOrder: () => 'bottom', renderer: () => ({ draw: (target) => this.draw(target) }) }];
  }
  private draw(target: CanvasRenderingTarget2D) {
    target.useMediaCoordinateSpace(({ context: ctx }) => {
      for (let i = 1; i < this.points.length; i++) {
        const previous = this.points[i - 1],
          current = this.points[i];
        const x1 = this.chart.timeScale().timeToCoordinate(previous.time),
          x2 = this.chart.timeScale().timeToCoordinate(current.time);
        const a1 = this.series.priceToCoordinate(previous.a),
          b1 = this.series.priceToCoordinate(previous.b),
          a2 = this.series.priceToCoordinate(current.a),
          b2 = this.series.priceToCoordinate(current.b);
        if (x1 === null || x2 === null || a1 === null || b1 === null || a2 === null || b2 === null)
          continue;
        ctx.fillStyle = current.a >= current.b ? 'rgba(5,150,105,0.10)' : 'rgba(225,29,72,0.08)';
        ctx.beginPath();
        ctx.moveTo(x1, a1);
        ctx.lineTo(x2, a2);
        ctx.lineTo(x2, b2);
        ctx.lineTo(x1, b1);
        ctx.closePath();
        ctx.fill();
      }
    });
  }
}
