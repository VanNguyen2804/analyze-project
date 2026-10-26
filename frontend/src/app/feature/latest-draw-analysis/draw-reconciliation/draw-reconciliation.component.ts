import { formatAnalysisNumber, formatAnalysisMetric, isNearMiss } from '../reconciliation-format';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  OnChanges,
  SimpleChanges,
  inject,
  input,
  output,
} from '@angular/core';
import { NgClass, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, takeUntil, timer } from 'rxjs';
import { AnalyzeService } from '../../../core/services/analyze.service';
import {
  ReconciliationDetailComponent,
  ReconciliationTab,
} from '../reconciliation-detail/reconciliation-detail.component';
@Component({
  selector: 'app-draw-reconciliation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass, DecimalPipe, FormsModule, ReconciliationDetailComponent],
  templateUrl: './draw-reconciliation.component.html',
  styleUrl: './draw-reconciliation.component.css',
})
export class DrawReconciliationComponent implements OnChanges {
  activeReconciliationTab: ReconciliationTab = 'aiPredictionJudgment';
  readonly isNearMiss = isNearMiss;
  readonly category = input('MEGA');
  readonly selectedDate = input('');
  readonly algorithm = input('temporal_learning');
  readonly version = input('');
  readonly versionChanged = output<string>();
  readonly refresh = output<void>();
  readonly statusChanged = output<string | null>();
  private readonly reconcileRequests = new Subject<void>();
  private readonly recommendationRequests = new Subject<void>();
  private readonly analyzeService = inject(AnalyzeService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  set algorithmUpdateMessage(message: string | null) {
    this.statusChanged.emit(message);
  }
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['version']) this.currentHyperparameterVersion = this.version();
    if (changes['category']) {
      this.load5DrawsReconciliation();
      this.loadRecommendations();
    }
  }
  reconciliationData: any = null;

  isLoadingReconciliation: boolean = false;

  reconcileError: string | null = null;

  selected5DrawIndex: number = 0;

  reconcileLimit: number = 5;

  reconcileAlgorithm: string = 'temporal_learning';

  selectedReconcileDate: string = '';

  availableLimits: number[] = [];

  showAllBacktestTable: boolean = false;

  isApplyingV150: boolean = false;

  recommendationsData: any = null;

  currentHyperparameterVersion: string = '';

  getLimitLabel(lim: number): string {
    return lim === this.reconciliationData?.totalDrawsInDb ? `All (${lim})` : String(lim);
  }

  loadRecommendations(): void {
    this.recommendationRequests.next();
    this.analyzeService
      .getPrediction(this.category(), this.algorithm())
      .pipe(takeUntil(this.recommendationRequests), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          if (res?.recommendations || res?.aiRecommendation) {
            this.recommendationsData = res.recommendations || res.aiRecommendation;
          }
          if (res?.hyperparameterVersion) {
            this.currentHyperparameterVersion = res.hyperparameterVersion;
            this.versionChanged.emit(this.currentHyperparameterVersion);
          }
          this.cdr.markForCheck();
        },
        error: (err) => console.error('Error loading recommendations:', err),
      });
  }

  load5DrawsReconciliation(): void {
    this.reconcileRequests.next();
    this.isLoadingReconciliation = true;
    this.reconcileError = null;
    this.cdr.markForCheck();
    this.analyzeService
      .get5DrawsReconciliation(
        this.category(),
        this.reconcileAlgorithm,
        this.reconcileLimit,
        this.selectedReconcileDate,
      )
      .pipe(takeUntil(this.reconcileRequests), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.reconciliationData = data;
          const count = Math.min(data?.totalDrawsInDb ?? 0, 100);
          this.availableLimits = [
            ...new Set([5, 10, 20, 50, count].filter((n) => n > 0 && n <= count)),
          ];
          if (data?.recommendations) {
            this.recommendationsData = data.recommendations;
          }
          if (data?.currentHyperparameterVersion) {
            this.currentHyperparameterVersion = data.currentHyperparameterVersion;
            this.versionChanged.emit(this.currentHyperparameterVersion);
          }
          if (this.selectedReconcileDate && data?.draws) {
            const idx = data.draws.findIndex((d: any) => d.drawDate === this.selectedReconcileDate);
            this.selected5DrawIndex = idx !== -1 ? idx : 0;
          } else if (this.selected5DrawIndex >= (data?.draws?.length || 0)) {
            this.selected5DrawIndex = 0;
          }
          this.isLoadingReconciliation = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          console.error('Failed to load reconciliation backtest:', err);
          this.reconcileError =
            'Không thể kết nối máy chủ để tính toán kiểm thử. Vui lòng bấm thử lại!';
          this.isLoadingReconciliation = false;
          this.cdr.markForCheck();
        },
      });
  }

  setReconcileLimit(lim: number): void {
    if (this.reconcileLimit === lim) return;
    this.reconcileLimit = lim;
    this.load5DrawsReconciliation();
  }

  setReconcileAlgorithm(alg: string): void {
    if (this.reconcileAlgorithm === alg) return;
    this.reconcileAlgorithm = alg;
    this.load5DrawsReconciliation();
  }

  onReconcileDateChange(date: string): void {
    this.selectedReconcileDate = date;
    if (!date) {
      this.load5DrawsReconciliation();
      return;
    }
    if (this.reconciliationData?.draws) {
      const idx = this.reconciliationData.draws.findIndex((d: any) => d.drawDate === date);
      if (idx !== -1) {
        this.selected5DrawIndex = idx;
        this.cdr.markForCheck();
        return;
      }
    }
    this.load5DrawsReconciliation();
  }

  select5Draw(index: number): void {
    this.selected5DrawIndex = index;
    if (this.reconciliationData?.draws?.[index]) {
      this.selectedReconcileDate = this.reconciliationData.draws[index].drawDate;
    }
    this.cdr.markForCheck();
  }

  getCurrent5Draw(): any {
    if (!this.reconciliationData?.draws || this.reconciliationData.draws.length === 0) return null;
    return (
      this.reconciliationData.draws[this.selected5DrawIndex] || this.reconciliationData.draws[0]
    );
  }

  applyV150Tuning(): void {
    if (!this.reconciliationData?.recommendedHyperparameters?.adjustments) return;
    const hyp = this.reconciliationData.recommendedHyperparameters;
    const targetVer = hyp.version || this.currentHyperparameterVersion;
    const testedDates = (this.reconciliationData?.draws || [])
      .map((d: any) => d.drawDate)
      .join(', ');
    this.isApplyingV150 = true;
    this.algorithmUpdateMessage = null;
    this.cdr.markForCheck();

    const readmeContent = `# Báo Cáo Hiệu Chỉnh Thuật Toán ${targetVer} (Khắc Phục Sai Lệch Các Kỳ Gần Nhất)

## 1. Bối cảnh & Dữ liệu Đối soát Thực Tế (Các Kỳ: ${testedDates})
- Tổng số vé đối chiếu thực tế: ${this.reconciliationData.overallSummary?.totalTickets || 0} vé
- Số vé trúng thưởng: ${this.reconciliationData.overallSummary?.winningTickets || 0} vé
- Số vé trượt: ${this.reconciliationData.overallSummary?.missedTickets || 0} vé
- Tỷ lệ khớp: ${this.reconciliationData.overallSummary?.hitRatePercent || 0}%

## 2. Các Nguyên Nhân Cốt Lõi Gây Sai Lệch Kết Quả:
${this.reconciliationData.overallSummary?.dominantFlaws?.map((f: string, i: number) => `${i + 1}. ${f}`).join('\n') || ''}

## 3. Các Hiệu Chỉnh Tham Số Trọng Yếu Trong Phiên Bản ${targetVer}:
${JSON.stringify(hyp.adjustments, null, 2)}`;

    const payload = {
      hyperparametersJson: JSON.stringify(hyp.adjustments, null, 2),
      category: this.category(),
      drawDate: hyp.drawDate || this.selectedDate() || new Date().toISOString().split('T')[0],
      readmeContent: readmeContent,
      note: `Hiệu chỉnh thuật toán ${targetVer}: Khắc phục sai lệch theo đối soát các kỳ quay gần nhất (${this.category()})`,
    };

    this.analyzeService
      .updateAlgorithm(payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res: any) => {
          this.isApplyingV150 = false;
          const version = res.record?.version || res.version || targetVer;
          this.currentHyperparameterVersion = version;
          this.algorithmUpdateMessage = `✅ Đã áp dụng thành công bộ tham số ${version} khắc phục sai lệch vào hệ thống!`;
          this.refresh.emit();
          this.cdr.markForCheck();
          timer(6000)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
              this.algorithmUpdateMessage = null;
              this.cdr.markForCheck();
            });
        },
        error: (err: any) => {
          this.isApplyingV150 = false;
          this.algorithmUpdateMessage =
            '❌ Lỗi khi cập nhật thuật toán: ' + (err.message || 'Lỗi server');
          this.cdr.markForCheck();
        },
      });
  }

  readonly formatMetric = formatAnalysisMetric;

  readonly formatNumber = formatAnalysisNumber;
}
