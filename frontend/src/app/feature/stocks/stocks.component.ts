import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe, PercentPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  BehaviorSubject,
  Subject,
  catchError,
  combineLatest,
  interval,
  of,
  switchMap,
  takeUntil,
  tap,
} from 'rxjs';
import { StockApiService } from '../../core/services/stock-api.service';
import {
  StockMode,
  StockSnapshot,
  StockPositionInput,
  StockPositionResult,
  StockWatchQuote,
} from '../../core/models/stock.model';
import { StockChartComponent } from './stock-chart.component';
import { StockRecommendationComponent } from './stock-recommendation.component';
import { StockDailyDeskComponent } from './stock-daily-desk.component';

@Component({
  selector: 'app-stocks',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    PercentPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    StockChartComponent,
    StockRecommendationComponent,
    StockDailyDeskComponent,
  ],
  templateUrl: './stocks.component.html',
  styleUrl: './stocks.component.css',
})
export class StocksComponent implements OnInit {
  private readonly api = inject(StockApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  private readonly modeChanges = new BehaviorSubject<StockMode>('SWING');
  private readonly refreshChanges = new BehaviorSubject(0);
  private readonly cancelPosition = new Subject<void>();
  readonly mode = signal<StockMode>('SWING');
  readonly data = signal<StockSnapshot | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly position = signal<StockPositionResult | null>(null);
  readonly positionBusy = signal(false);
  readonly positionError = signal('');
  readonly symbols = ['VIC', 'FPT', 'SZC', 'MBB'];
  readonly quotes = signal<Record<string, StockWatchQuote>>({});
  input: StockPositionInput = {
    purchaseDate: '',
    purchasePrice: null,
    quantity: null,
    maxLossPercent: 10,
    sellableQuantity: null,
  };
  ngOnInit() {
    this.refreshChanges
      .pipe(
        switchMap(() => this.api.watchlist().pipe(catchError(() => of([])))),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((quotes) => this.quotes.set(Object.fromEntries(quotes.map((q) => [q.symbol, q]))));
    combineLatest([this.route.paramMap, this.modeChanges, this.refreshChanges])
      .pipe(
        tap(([params, mode]) => {
          this.loading.set(true);
          this.error.set('');
          const current = this.data();
          if (current?.symbol !== (params.get('symbol') || 'FPT') || current?.mode !== mode)
            this.data.set(null);
          this.resetPosition();
        }),
        switchMap(([params, mode]) =>
          this.api.analyze(params.get('symbol') || 'FPT', mode).pipe(
            catchError((err) => {
              this.error.set(err.error?.message || 'Không tải được dữ liệu. Hãy thử lại.');
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((data) => {
        if (data) this.data.set(data);
        this.loading.set(false);
      });
    interval(60000)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(() => {
        if (!document.hidden && !this.loading() && !this.positionBusy()) this.refresh();
      });
    this.destroy.onDestroy(() => {
      this.cancelPosition.next();
      this.cancelPosition.complete();
    });
  }
  setMode(mode: StockMode) {
    if (mode !== this.mode()) {
      this.mode.set(mode);
      this.modeChanges.next(mode);
    }
  }
  refresh() {
    this.refreshChanges.next(this.refreshChanges.value + 1);
  }
  resetPosition() {
    this.cancelPosition.next();
    this.position.set(null);
    this.positionError.set('');
    this.positionBusy.set(false);
  }
  analyzePosition() {
    const s = this.data();
    if (!s || this.positionBusy() || this.loading()) return;
    this.resetPosition();
    this.positionBusy.set(true);
    this.api
      .position(s.symbol, s.snapshotId, { ...this.input, maxLossPercent: 10 })
      .pipe(takeUntil(this.cancelPosition), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (result) => {
          this.position.set(result);
          this.positionBusy.set(false);
        },
        error: (err) => {
          this.positionError.set(err.error?.message || 'Không phân tích được vị thế.');
          this.positionBusy.set(false);
        },
      });
  }
  label(signal: string) {
    return (
      (
        {
          BUY: 'Cân nhắc mua',
          SELL: 'Cân nhắc giảm / bán',
          HOLD: 'Giữ',
          WATCH: 'Theo dõi',
          UNAVAILABLE: 'Chưa đủ dữ liệu',
        } as Record<string, string>
      )[signal] || signal
    );
  }
  unitLabel(unit: string) {
    return unit === 'VND'
      ? 'VND/cổ phiếu'
      : unit === 'THOUSAND_VND'
        ? 'Nghìn VND/cổ phiếu'
        : 'Đơn vị nguồn chưa xác minh';
  }
  latestPrice(s: StockSnapshot) {
    return s.data.candles.at(-1)?.close ?? null;
  }
  benchmarkPrice(s: StockSnapshot) {
    return s.benchmark.candles.at(-1)?.close ?? null;
  }
}
