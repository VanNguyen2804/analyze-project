import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { NgClass } from '@angular/common';

import { CategoryLinkageChartData } from '../../../core/models/prediction-payload.model';
@Component({
  selector: 'app-spacing-distribution',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass],
  templateUrl: './spacing-distribution.component.html',
  styleUrl: './spacing-distribution.component.css',
})
export class SpacingDistributionComponent {
  readonly linkageChartData = input<CategoryLinkageChartData | null>(null);

  get observedPeakSumPercent(): number | null {
    const items = this.linkageChartData()?.sumDistributions ?? [];
    return items.length ? Math.max(...items.map((item) => item.percent)) : null;
  }
}
