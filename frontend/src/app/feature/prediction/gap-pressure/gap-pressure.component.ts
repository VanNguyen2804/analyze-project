import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  input,
} from '@angular/core';
import { NgClass } from '@angular/common';

import {
  CategoryLinkageChartData,
  NumberGapPressureItem,
} from '../../../core/models/prediction-payload.model';
@Component({
  selector: 'app-gap-pressure',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass],
  templateUrl: './gap-pressure.component.html',
  styleUrl: './gap-pressure.component.css',
})
export class GapPressureComponent {
  readonly linkageChartData = input<CategoryLinkageChartData | null>(null);
  private readonly cdr = inject(ChangeDetectorRef);
  gapFilterZone: 'ALL' | 'HOT' | 'CYCLE' | 'PRESSURE' | 'OVERDUE' = 'ALL';

  selectedPressureNumber: NumberGapPressureItem | null = null;

  setGapFilterZone(zone: 'ALL' | 'HOT' | 'CYCLE' | 'PRESSURE' | 'OVERDUE') {
    this.gapFilterZone = zone;
    this.cdr.markForCheck();
  }

  selectPressureNumber(item: NumberGapPressureItem) {
    if (this.selectedPressureNumber?.number === item.number) {
      this.selectedPressureNumber = null;
    } else {
      this.selectedPressureNumber = item;
    }
    this.cdr.markForCheck();
  }

  getFilteredGapPressureList(): NumberGapPressureItem[] {
    const data = this.linkageChartData();
    if (!data?.gapPressureList) return [];
    if (this.gapFilterZone === 'ALL') return data.gapPressureList;
    return data.gapPressureList.filter((item) => item.zone === this.gapFilterZone);
  }
}
