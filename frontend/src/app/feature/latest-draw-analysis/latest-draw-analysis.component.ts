import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, distinctUntilChanged, switchMap, takeUntil } from 'rxjs';
import { AnalyzeService } from '../../core/services/analyze.service';
import { AnalyzedTicketGroup, DrawReport } from '../../core/models/draw-report.model';
import { AnalysisTicketGroupComponent } from './ticket-group/ticket-group.component';
import { EvidenceDetailsComponent } from './evidence-details/evidence-details.component';
import { DrawReconciliationComponent } from './draw-reconciliation/draw-reconciliation.component';
import { formatAnalysisNumber } from './reconciliation-format';

interface Query {
  category: string;
  date: string;
  algorithm: string;
  evidenceId: string;
}

@Component({
  selector: 'app-latest-draw-analysis',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    DatePipe,
    RouterLink,
    AnalysisTicketGroupComponent,
    EvidenceDetailsComponent,
    DrawReconciliationComponent,
  ],
  templateUrl: './latest-draw-analysis.component.html',
  styleUrl: './latest-draw-analysis.component.css',
})
export class LatestDrawAnalysisComponent implements OnInit {
  private readonly api = inject(AnalyzeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly requests = new Subject<Query>();
  private readonly manualRequests = new Subject<void>();
  readonly report = signal<DrawReport | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly manualGroup = signal<AnalyzedTicketGroup | null>(null);
  readonly manualBusy = signal(false);
  readonly manualError = signal('');
  readonly copyMessage = signal('');
  readonly category = signal('MEGA');
  readonly userGroups = computed(
    () => this.report()?.ticketGroups.filter((g) => g.timing !== 'SAVED_BEFORE_DRAW') ?? [],
  );
  readonly selectedEvidence = computed(() =>
    this.report()?.evidence.find((e) => e.id === this.report()?.selectedEvidenceId),
  );
  readonly selectedForecast = computed(() =>
    this.report()?.ticketGroups.find((g) => g.id === this.report()?.selectedEvidenceId),
  );
  readonly formatNumber = formatAnalysisNumber;
  dateDraft = '';
  algorithmDraft = 'temporal_learning';
  manualText = '';
  algorithms: { id: string; name: string }[] = [];

  ngOnInit(): void {
    this.requests
      .pipe(
        switchMap((query) => {
          this.busy.set(true);
          this.error.set('');
          this.report.set(null);
          this.manualRequests.next();
          this.manualGroup.set(null);
          this.manualBusy.set(false);
          this.manualError.set('');
          this.manualText = '';
          this.copyMessage.set('');
          return this.api
            .getDrawReport(query.category, query.date, query.algorithm, query.evidenceId)
            .pipe(
              catchError((err) => {
                this.busy.set(false);
                this.error.set(err.error?.message || 'Không tải được báo cáo. Hãy thử lại.');
                return EMPTY;
              }),
            );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((report) => {
        this.busy.set(false);
        if (report.context.category !== this.category()) {
          this.error.set('Dữ liệu trả về không khớp loại xổ số.');
          return;
        }
        this.report.set(report);
        this.dateDraft = report.context.drawDate ?? '';
        this.cdr.markForCheck();
      });
    this.api
      .getAlgorithms()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (catalog) => {
          this.algorithms = [
            ...catalog.map((a) => ({ id: a.id, name: a.name })),
            { id: 'combined_ensemble', name: 'Bộ kết hợp đã lưu' },
            { id: 'lab-logistic', name: 'AI Lab · Logistic' },
            { id: 'lab-recency', name: 'AI Lab · Tần suất suy giảm' },
          ];
          this.cdr.markForCheck();
        },
        error: () => this.error.set('Không tải được danh mục thuật toán.'),
      });
    this.api.currentCategory$
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((category) => {
        this.category.set(category);
        this.fetchAnalysis(true);
      });
  }
  changeCategory(category: string): void {
    if (category !== this.category()) this.api.setCategory(category);
  }
  fetchAnalysis(latest = false): void {
    this.requests.next({
      category: this.category(),
      date: latest ? '' : this.dateDraft,
      algorithm: this.algorithmDraft,
      evidenceId: '',
    });
  }
  selectEvidence(id: string): void {
    const context = this.report()?.context;
    if (context)
      this.requests.next({
        category: context.category,
        date: context.drawDate ?? '',
        algorithm: context.algorithm,
        evidenceId: id,
      });
  }
  evaluateManual(): void {
    const context = this.report()?.context;
    if (!context?.drawDate || !this.report()?.draw) return;
    this.manualError.set('');
    const lines = this.manualText
      .trim()
      .split(/\r?\n/)
      .filter((line) => line.trim());
    const max = context.category === 'POWER' ? 55 : 45;
    const tickets: number[][] = [];
    if (!lines.length || lines.length > 25) {
      this.manualError.set('Nhập từ 1 đến 25 vé, mỗi vé một dòng.');
      return;
    }
    for (const line of lines) {
      const tokens = line.trim().split(/[\s,;|]+/);
      const numbers = tokens.map(Number);
      if (
        tokens.length !== 6 ||
        tokens.some((t) => !/^\d+$/.test(t)) ||
        new Set(numbers).size !== 6 ||
        numbers.some((n) => !Number.isInteger(n) || n < 1 || n > max)
      ) {
        this.manualError.set(
          `Mỗi dòng cần đúng sáu số nguyên khác nhau từ 1 đến ${max}; không bỏ qua số sai.`,
        );
        return;
      }
      tickets.push(numbers);
    }
    this.manualRequests.next();
    this.manualBusy.set(true);
    this.manualGroup.set(null);
    this.api
      .evaluateTickets(context.category, context.drawDate, tickets)
      .pipe(
        takeUntil(this.manualRequests),
        takeUntil(this.requests),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (group) => {
          this.manualGroup.set(group);
          this.manualBusy.set(false);
        },
        error: (err) => {
          this.manualError.set(err.error?.message || 'Không dò được vé. Hãy thử lại.');
          this.manualBusy.set(false);
        },
      });
  }
  reportText(): string {
    const report = this.report();
    if (!report) return '';
    const groups = [...this.userGroups(), ...(this.manualGroup() ? [this.manualGroup()!] : [])];
    return [
      `ĐỐI CHIẾU ${report.context.category} · ${report.context.drawDate}`,
      `Thuật toán đang xem: ${report.context.algorithm}`,
      `Kết quả: ${report.draw?.numbers.join(' · ') ?? 'Chưa có kết quả hợp lệ'}; số phụ: ${report.draw?.specialNumber ?? 'Không có'}`,
      `Nguồn kết quả: ${report.draw?.sourceUrl ?? 'Chưa có'}; chất lượng: ${report.draw?.quality ?? 'Chưa có'}`,
      ...groups.flatMap((g) => [
        `${g.label} (${g.timing}): ${g.portfolio.ticketCount} vé khác nhau, ${g.portfolio.uniqueNumberCount} số đã chọn; vé tốt nhất ${g.portfolio.bestMatched ?? 'N/A'}/6; độ phủ ${g.portfolio.coverageCount}/6 [${g.portfolio.coverage.join(', ')}]; ${g.portfolio.rawMatchedOccurrences} lượt khớp.`,
        ...g.tickets.map(
          (t) =>
            `${t.numbers.join(' ')} -> ${t.matchedCount}/6 [${t.matchedNumbers.join(', ')}] · ${t.prize}`,
        ),
      ]),
      `Snapshot: ${report.selectedEvidenceId ?? 'Không có'}; giải thích: ${this.selectedEvidence()?.explanationStatus ?? 'Chưa lưu'}`,
      ...report.numberDetails.map(
        (d) =>
          `Số ${d.number}: hạng ${d.rank ?? 'chưa lưu'}, điểm nội bộ ${d.score ?? 'chưa lưu'}, tần suất ${d.frequency ?? 'chưa lưu'}, khoảng cách ${d.drawGap ?? 'chưa lưu'} kỳ. Thành phần đã lưu: ${
            Object.entries(d.features)
              .map(([key, value]) => `${key}=${value}`)
              .join(', ') || 'Chưa lưu'
          }.`,
      ),
      ...report.evaluation.map(
        (e) =>
          `${e.algorithm} · ${e.modelVersion}: ${e.evaluatedDraws} kỳ, ${e.ticketBudget} vé/kỳ; khớp TB/vé ${e.averageMatched ?? 'N/A'}, độ phủ TB/kỳ ${e.coverageAverage ?? 'N/A'}, kỳ có vé ≥3 ${e.hit3RatePercent ?? 'N/A'}%; đối chứng ghép cặp ${e.pairedDraws} kỳ.`,
      ),
      ...report.warnings,
      'Báo cáo không thay cấu hình hoặc huấn luyện lại mô hình.',
    ].join('\n');
  }
  async copyReport(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.reportText());
      this.copyMessage.set('Đã sao chép báo cáo.');
    } catch {
      this.copyMessage.set('Không truy cập được clipboard; bạn có thể tải báo cáo.');
    }
  }
  downloadReport(): void {
    const report = this.report();
    if (!report) return;
    const url = URL.createObjectURL(
      new Blob([this.reportText()], { type: 'text/plain;charset=utf-8' }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = `analyze-${report.context.category}-${report.context.drawDate}.txt`;
    link.click();
    URL.revokeObjectURL(url);
  }
}
