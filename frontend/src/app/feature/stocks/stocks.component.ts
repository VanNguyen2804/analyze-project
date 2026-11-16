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
import { ActivatedRoute, RouterLink, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BackendReadinessService } from '../../core/services/backend-readiness.service';
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
import { StockApiService, TradingPlan } from '../../core/services/stock-api.service';
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
import { StockExecutionDraft } from './stock-position-plan.component';
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
    StockCandleScenariosComponent,
  ],
  templateUrl: './stocks.component.html',
  styleUrl: './stocks.component.css',
})
export class StocksComponent implements OnInit {
  readonly backend = inject(BackendReadinessService);
  private readonly api = inject(StockApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  private readonly sessions = inject(StockPositionSessions);
  private session = this.sessions.forSymbol('VIC');
  private activeSymbol = 'VIC';
  private readonly modeChanges = new BehaviorSubject<StockMode>('SWING');
  private readonly refreshChanges = new BehaviorSubject(0);
  private readonly quoteRefreshChanges = new BehaviorSubject(0);
  private readonly cancelPosition = new Subject<void>();
  readonly tradingPlans = signal<TradingPlan[]>([]);
  readonly planMessage = signal('Nhập vị thế để lập kế hoạch mua bán.');
  loadTradingPlans() {
    const symbol = this.activeSymbol, signature = JSON.stringify(this.input);
    this.tradingPlans.set([]);
    if (!this.input.purchasePrice || !this.input.quantity) return;
    this.planMessage.set('Đang lập hai kế hoạch…');
    this.api.tradingPlans(symbol, this.input).pipe(takeUntilDestroyed(this.destroy)).subscribe({next: r => { if (symbol === this.activeSymbol && signature === JSON.stringify(this.input)) { this.tradingPlans.set(r.plans); this.planMessage.set(r.message || ''); } }, error: () => this.planMessage.set('Chưa lập được kế hoạch. Hãy cập nhật lại vị thế.')});
  }
  readonly checkpoints = signal<Array<{checkpoint: string; capturedAt: string; swing: StockSnapshot; intraday: StockSnapshot; swingPosition?: StockPositionResult; intradayPosition?: StockPositionResult; longTermNote: string}>>([]);
  readonly checkpointError = signal('');
  readonly selectedPlan = computed(() => this.tradingPlans().find(p => p.id === this.horizon()));
  readonly planCandles = computed(() => {
    const candles = this.data()?.data.candles ?? [];
    return this.horizon() === 'SWING' ? candles.slice(-90) : candles;
  });
  selectHorizon(value: 'SWING' | 'LONG') { this.horizon.set(value); }
  readonly horizon = signal<'LONG' | 'SWING'>('SWING');
  hasCheckpoint(slot: string) { return this.checkpoints().some(r => r.checkpoint === slot); }
  loadCheckpoints(symbol: string) {
    this.api.checkpoints(symbol).pipe(takeUntilDestroyed(this.destroy)).subscribe({next: rows => { if(this.activeSymbol === symbol) { this.checkpoints.set(rows); this.checkpointError.set(''); } },error: () => this.checkpointError.set('Không tải được báo cáo theo mốc giờ.')});
  }
  readonly mode = signal<StockMode>('SWING');
  readonly data = signal<StockSnapshot | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly position = signal<StockPositionResult | null>(null);
  readonly positionBusy = signal(false);
  readonly positionError = signal('');
  readonly positionStale = signal(false);
  readonly symbols = this.loadSymbols();
  private loadSymbols(): string[] { return ['VIC']; }
  readonly saveStatus = signal('');
  private saveTimer: ReturnType<typeof setTimeout> | undefined;
  private editVersion = 0;
  private persistPosition() {
    clearTimeout(this.saveTimer);
    const input = structuredClone(this.input), symbol = this.activeSymbol, session = this.session, version = this.editVersion;
    if (!input.purchasePrice || !input.quantity || input.purchasePrice <= 0 || input.quantity <= 0 || !Number.isInteger(input.quantity)) return;
    this.saveStatus.set('Đang lưu vị thế…');
    this.saveTimer = setTimeout(() => this.api.savePosition(symbol, input).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => { session.autoAnalyze = true; if (this.session === session && this.editVersion === version) { this.saveStatus.set('Đã lưu vị thế trên máy chủ'); this.analyzePosition(); } },
      error: () => this.saveStatus.set('Chưa lưu được trên máy chủ. Hãy thử cập nhật lại.')
    }), 600);
  }
  readonly symbolError = signal('');
  addSymbol(raw: string) {
    const symbol = raw.trim().toUpperCase();
    if (!/^[A-Z][A-Z0-9]{2,9}$/.test(symbol)) {
      this.symbolError.set('Nhập mã hợp lệ (3–10 ký tự).');
      return;
    }
    this.symbolError.set('');
    if (!this.symbols.includes(symbol)) this.symbols.push(symbol);
    try { localStorage.setItem('stocks.watchlist.v1', JSON.stringify(this.symbols)); } catch {}
    void this.router.navigate(['/stocks', symbol]);
  }
  readonly quotes = signal<Record<string, StockWatchQuote>>({});
  get input(): StockPositionInput {
    return this.session.input;
  }
  set input(value: StockPositionInput) {
    this.session.input = value;
    this.resetPosition();
  }
  readonly chartLevels = computed<StockPlanLevel[]>(() => {
    const s = this.data(), plan = this.selectedPlan();
    if (!s || !plan || this.positionStale() || !s.data.verified || s.data.priceBasis !== 'RAW') return [];
    const factor = s.data.unit === 'VND' ? 1 : s.data.unit === 'THOUSAND_VND' ? 1000 : 0;
    if (!factor) return [];
    return [
      { price: plan.entryLow / factor, title: 'Vùng mua thấp', color: '#38bdf8' },
      { price: plan.entryHigh / factor, title: 'Vùng mua cao', color: '#38bdf8' },
      { price: plan.target1 / factor, title: 'Chốt lời 1', color: '#2dd4bf' },
      { price: plan.target2 / factor, title: 'Chốt lời 2', color: '#2dd4bf' },
      { price: plan.stop / factor, title: 'Bảo vệ vốn', color: '#fb7185' },
    ];
  });
  ngOnInit() {
    const initialVersion = this.editVersion;
    this.api.savedPositions().pipe(takeUntilDestroyed(this.destroy)).subscribe({next: saved => {
      if (this.editVersion !== initialVersion) return;
      for (const [symbol, input] of Object.entries(saved)) {
        const session = this.sessions.forSymbol(symbol);
        session.input = { ...newStockPosition(), ...input }; session.autoAnalyze = true;
      }
      this.session = this.sessions.forSymbol(this.activeSymbol);
      if (this.session.autoAnalyze) this.analyzePosition();
    }, error: () => this.saveStatus.set('Không tải được vị thế đã lưu trên máy chủ.')});

    this.quoteRefreshChanges
      .pipe(
        switchMap(() => this.api.watchlist().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((quotes) => {
        if (quotes) this.quotes.set(Object.fromEntries(quotes.map((q) => [q.symbol, q])));
      });
    combineLatest([this.route.paramMap, this.modeChanges, this.refreshChanges])
      .pipe(
        tap(([params, mode]) => {
          this.loading.set(true);
          this.error.set('');
          this.cancelPosition.next();
          this.positionBusy.set(false);
          this.positionError.set('');
          const symbol = params.get('symbol') || 'VIC',
            current = this.data();
          if (symbol !== this.activeSymbol) {
            this.activeSymbol = symbol;
            this.checkpoints.set([]);
            this.session = this.sessions.forSymbol(symbol);
            this.position.set(this.session.result);
          }
          if (current?.symbol !== symbol || current?.mode !== mode) this.data.set(null);
          this.positionStale.set(!!this.position());
        }),
        switchMap(([params, mode]) =>
          this.api.analyze(params.get('symbol') || 'VIC', mode).pipe(
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
        if (data) { this.data.set(data); this.loadCheckpoints(data.symbol); }
        this.loading.set(false);
        if (data && this.session.autoAnalyze) this.analyzePosition();
      });
    interval(60000)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe((minute) => {
        if (!document.hidden && !this.loading() && !this.positionBusy() && !this.error()) {
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
    this.tradingPlans.set([]);
    this.editVersion++;
    this.persistPosition();
    this.sessions.save();
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
    this.loadTradingPlans();
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
          this.sessions.save();
          this.api.savePosition(this.activeSymbol, session.input).pipe(takeUntilDestroyed(this.destroy)).subscribe({error: () => this.saveStatus.set("Chưa lưu được kế hoạch trên máy chủ.")});
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
