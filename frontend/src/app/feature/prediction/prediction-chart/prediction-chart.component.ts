import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  OnInit,
  inject,
} from '@angular/core';
import { NgClass } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { distinctUntilChanged, Subject, takeUntil } from 'rxjs';
import { AnalyzeService } from '../../../core/services/analyze.service';
import { CategoryLinkageChartData } from '../../../core/models/prediction-payload.model';
import { buildLinkageChartData, LinkageHistoryRecord } from '../linkage-chart';
import { LinkageTimelineComponent } from '../linkage-timeline/linkage-timeline.component';
import { GapPressureComponent } from '../gap-pressure/gap-pressure.component';
import { SpacingDistributionComponent } from '../spacing-distribution/spacing-distribution.component';
@Component({
  selector: 'app-prediction-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass, LinkageTimelineComponent, GapPressureComponent, SpacingDistributionComponent],
  templateUrl: './prediction-chart.component.html',
  styleUrl: './prediction-chart.component.css',
})
export class PredictionChartComponent implements OnInit {
  private readonly analyzeService = inject(AnalyzeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly historyRequests = new Subject<void>();
  ngOnInit(): void {
    this.analyzeService.currentCategory$
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((category) => {
        this.chartCategory = category;
        this.loadChartDataForCategory(category);
      });
  }
  chartCategory: 'MEGA' | 'POWER' = 'MEGA';

  chartMode: 'timeline' | 'gap_pressure' | 'delta_distribution' = 'timeline';

  linkageChartData: CategoryLinkageChartData | null = null;

  cachedHistoryRecords: Partial<Record<'MEGA' | 'POWER', LinkageHistoryRecord[]>> = {};

  isChartLoading: boolean = false;

  switchChartCategory(cat: 'MEGA' | 'POWER') {
    if (this.analyzeService.category() !== cat) {
      this.analyzeService.setCategory(cat);
    } else {
      this.loadChartDataForCategory(cat);
    }
  }

  setChartMode(mode: 'timeline' | 'gap_pressure' | 'delta_distribution') {
    this.chartMode = mode;
    this.cdr.markForCheck();
  }

  loadChartDataForCategory(cat: 'MEGA' | 'POWER') {
    this.historyRequests.next();
    if (this.cachedHistoryRecords[cat] && this.cachedHistoryRecords[cat].length > 0) {
      this.isChartLoading = false;
      this.computeLinkageChartData(cat, this.cachedHistoryRecords[cat] ?? []);
      return;
    }
    this.isChartLoading = true;
    this.cdr.markForCheck();
    this.analyzeService
      .getHistory(cat)
      .pipe(takeUntil(this.historyRequests), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (draws) => {
          this.cachedHistoryRecords[cat] = Array.isArray(draws) ? draws : [];
          this.computeLinkageChartData(cat, this.cachedHistoryRecords[cat]);
          this.isChartLoading = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          console.error('Lỗi tải dữ liệu lịch sử cho biểu đồ:', err);
          this.isChartLoading = false;
          this.cdr.markForCheck();
        },
      });
  }

  computeLinkageChartData(cat: 'MEGA' | 'POWER', records: LinkageHistoryRecord[]): void {
    this.linkageChartData = buildLinkageChartData(cat, records);
    this.cdr.markForCheck();
  }
}
