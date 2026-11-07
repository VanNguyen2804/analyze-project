import { StockCandleScenariosComponent } from './stock-candle-scenarios.component';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe, PercentPipe, DatePipe } from '@angular/common';
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
  StockPlanLevel,
} from '../../core/models/stock.model';
import { StockChartComponent } from './stock-chart.component';
import { StockRecommendationComponent } from './stock-recommendation.component';
import { StockDailyDeskComponent } from './stock-daily-desk.component';
import { StockPositionFormComponent } from './stock-position-form.component';
import { StockPositionSummaryComponent } from './stock-position-summary.component';
import { StockPositionPlanComponent, StockExecutionDraft } from './stock-position-plan.component';
import { StockPositionSessions, newStockPosition } from './stock-position-session';

@Component({
  selector: 'app-stocks',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    PercentPipe,
    DatePipe,
    RouterLink,
    StockChartComponent,
    StockRecommendationComponent,
    StockDailyDeskComponent,
    StockPositionFormComponent,
    StockPositionSummaryComponent,
    StockPositionPlanComponent,
    StockCandleScenariosComponent,
  ],
  templateUrl: './stocks.component.html',
  styleUrl: './stocks.component.css',
})
export class StocksComponent implements OnInit {
  private readonly api = inject(StockApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  private readonly sessions = inject(StockPositionSessions);
  private session = this.sessions.forSymbol('FPT');
  private activeSymbol = 'FPT';
  private readonly modeChanges = new BehaviorSubject<StockMode>('SWING');
  private readonly refreshChanges = new BehaviorSubject(0);
  private readonly quoteRefreshChanges = new BehaviorSubject(0);
  private readonly cancelPosition = new Subject<void>();
  readonly mode = signal<StockMode>('SWING');
  readonly data = signal<StockSnapshot | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly position = signal<StockPositionResult | null>(null);
  readonly positionBusy = signal(false);
  readonly positionError = signal('');
  readonly positionStale = signal(false);
  readonly symbols = ['VIC', 'FPT', 'SZC', 'MBB'];
  readonly quotes = signal<Record<string, StockWatchQuote>>({});
  get input(): StockPositionInput {
    return this.session.input;
  }
  set input(value: StockPositionInput) {
    this.session.input = value;
    this.resetPosition();
  }
  readonly chartLevels = computed<StockPlanLevel[]>(() => {
    const p = this.position()?.positionPlan,
      s = this.data();
    if (!s || this.positionStale() || !s.data.verified || s.data.priceBasis !== 'RAW') return [];
    const factor = s.data.unit === 'VND' ? 1 : s.data.unit === 'THOUSAND_VND' ? 1000 : 0;
    if (!factor) return [];
    const scenarios = this.position()?.scenarios;
    if (scenarios)
      return [
        { price: scenarios.averageCost / factor, title: 'Giá vốn', color: '#a78bfa' },
        ...scenarios.items
          .filter((item) => item.suggestedQuantity > 0)
          .map((item) => ({
            price: item.triggerPrice / factor,
            title: item.title,
            color: item.side === 'BUY' ? '#38bdf8' : '#fb7185',
          })),
      ];
    if (!p) return [];

    return [
      { price: p.averageCost / factor, title: 'Giá vốn', color: '#a78bfa' },
      { price: p.alertPrice / factor, title: 'Cảnh báo lỗ', color: '#fbbf24' },
      { price: p.effectiveStop / factor, title: 'Dừng rủi ro', color: '#fb7185' },
      ...p.steps
        .filter((t) => t.id !== 'REDUCE' && t.status !== 'DONE' && t.quantity > 0)
        .map((t) => ({
          price: t.triggerPrice / factor,
          title: t.id,
          color: t.side === 'BUY' ? '#38bdf8' : '#2dd4bf',
        })),
    ];
  });
  ngOnInit() {
    this.quoteRefreshChanges
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
          this.cancelPosition.next();
          this.positionBusy.set(false);
          this.positionError.set('');
          const symbol = params.get('symbol') || 'FPT',
            current = this.data();
          if (symbol !== this.activeSymbol) {
            this.activeSymbol = symbol;
            this.session = this.sessions.forSymbol(symbol);
            this.position.set(this.session.result);
          }
          if (current?.symbol !== symbol || current?.mode !== mode) this.data.set(null);
          this.positionStale.set(!!this.position());
        }),
        switchMap(([params, mode]) =>
          this.api.analyze(params.get('symbol') || 'FPT', mode).pipe(
            catchError((err) => {
              this.error.set(
                err.name === 'TimeoutError'
                  ? 'Máy chủ phản hồi chậm. Hãy thử tải lại; dữ liệu đang xem được giữ nguyên.'
                  : err.error?.message || 'Không tải được dữ liệu. Hãy thử lại.',
              );
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((data) => {
        if (data) this.data.set(data);
        this.loading.set(false);
        if (data && this.session.autoAnalyze) this.analyzePosition();
      });
    interval(60000)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe((minute) => {
        if (!document.hidden && !this.loading() && !this.positionBusy()) {
          this.refreshChanges.next(this.refreshChanges.value + 1);
          if ((minute + 1) % 5 === 0)
            this.quoteRefreshChanges.next(this.quoteRefreshChanges.value + 1);
        }
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
    this.quoteRefreshChanges.next(this.quoteRefreshChanges.value + 1);
  }
  resetPosition() {
    this.cancelPosition.next();
    this.positionBusy.set(false);
    this.positionError.set('');
    this.positionStale.set(true);
    this.session.autoAnalyze = false;
    if (this.input.plan && !this.input.plan.executions.length) {
      this.input.plan.availableQuantity = null;
      this.input.plan.observedPeak = null;
      this.input.plan.startedAt = new Date().toISOString();
    }
  }
  analyzePosition(candidate?: StockPositionInput) {
    const s = this.data();
    if (!s || this.positionBusy() || this.loading()) return;
    this.cancelPosition.next();
    this.positionBusy.set(true);
    this.positionError.set('');
    const session = this.session,
      signature = JSON.stringify(session.input),
      request = structuredClone(candidate ?? session.input);
    this.api
      .position(s.symbol, s.snapshotId, request)
      .pipe(takeUntil(this.cancelPosition), takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (result) => {
          if (
            this.session !== session ||
            this.data()?.snapshotId !== s.snapshotId ||
            signature !== JSON.stringify(session.input)
          ) {
            this.positionBusy.set(false);
            return;
          }
          if (candidate && !result.positionPlan) {
            this.positionError.set(
              'Chưa ghi nhận giao dịch: cần dữ liệu hợp lệ để tính lại vị thế.',
            );
            this.positionBusy.set(false);
            return;
          }
          if (result.snapshotId !== s.snapshotId) {
            this.positionError.set('Kết quả khác snapshot đang xem. Hãy cập nhật lại.');
            this.positionBusy.set(false);
            return;
          }
          if (candidate) session.input = request;
          if (session.input.plan && result.positionPlan) {
            session.input.plan.observedPeak = result.positionPlan.observedPeak;
            session.input.plan.availableQuantity = result.positionPlan.sellableQuantity;
          }
          session.result = result;
          session.autoAnalyze = true;
          this.position.set(result);
          this.positionStale.set(false);
          this.positionBusy.set(false);
        },
        error: (err) => {
          this.positionError.set(err.error?.message || 'Không phân tích được vị thế.');
          this.positionBusy.set(false);
          this.positionStale.set(true);
        },
      });
  }
  recordExecution(draft: StockExecutionDraft) {
    const plan = this.position()?.positionPlan,
      step = plan?.steps.find((t) => t.id === draft.stepId);
    if (
      !plan ||
      !step ||
      !this.input.plan ||
      this.positionStale() ||
      this.loading() ||
      this.positionBusy()
    )
      return;
    if (
      ['DONE', 'BLOCKED', 'WAIT_PREVIOUS', 'NEED_SELLABLE'].includes(step.status) ||
      draft.quantity <= 0 ||
      !Number.isInteger(draft.quantity) ||
      draft.quantity > step.quantity ||
      !Number.isFinite(draft.price) ||
      draft.price <= 0
    )
      return;
    const candidate = structuredClone(this.input);
    candidate.plan!.executions.push({
      ...draft,
      executedAt: new Date().toISOString(),
      sellableBefore: step.side === 'SELL' ? plan.sellableQuantity : null,
    });
    candidate.plan!.availableQuantity =
      plan.sellableQuantity === null
        ? null
        : plan.sellableQuantity - (step.side === 'SELL' ? draft.quantity : 0);
    this.analyzePosition(candidate);
  }
  restartPlan() {
    const p = this.position()?.positionPlan;
    if (!p || this.positionStale() || this.positionBusy() || this.loading()) {
      this.positionError.set('Cập nhật vị thế trước khi lập kế hoạch mới.');
      return;
    }
    const next = newStockPosition();
    next.purchasePrice = p.averageCost;
    next.quantity = p.quantity || null;
    this.input = next;
    this.session.result = null;
    this.position.set(null);
  }
  purchaseChartPrice(s: StockSnapshot) {
    if (!s.data.verified || s.data.priceBasis !== 'RAW') return null;
    const value = this.positionStale()
      ? this.input.purchasePrice
      : (this.position()?.positionPlan?.averageCost ?? this.input.purchasePrice);
    return value === null
      ? null
      : s.data.unit === 'VND'
        ? value
        : s.data.unit === 'THOUSAND_VND'
          ? value / 1000
          : null;
  }
  label(value: string) {
    return (
      (
        {
          BUY: 'Bình giá có điều kiện',
          SELL: 'Đánh giá giảm / bán',
          HOLD: 'Theo dõi vị thế',
          WATCH: 'Chờ xác nhận',
          UNAVAILABLE: 'Chưa đủ dữ liệu',
        } as Record<string, string>
      )[value] || value
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
  dayChange(s: StockSnapshot) {
    const bars = s.data.candles;
    return bars.length < 2 ? null : (bars.at(-1)!.close / bars.at(-2)!.close - 1) * 100;
  }
}
