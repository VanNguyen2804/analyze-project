import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  input,
} from '@angular/core';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  CategoryLinkageChartData,
  SequenceTransitionLink,
} from '../../../core/models/prediction-payload.model';
@Component({
  selector: 'app-linkage-timeline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass, FormsModule],
  templateUrl: './linkage-timeline.component.html',
  styleUrl: './linkage-timeline.component.css',
})
export class LinkageTimelineComponent {
  readonly linkageChartData = input<CategoryLinkageChartData | null>(null);
  private readonly cdr = inject(ChangeDetectorRef);
  showRepeatLinks: boolean = true;

  showDriftLinks: boolean = true;

  showSameTailLinks: boolean = true;

  showSpecialMigrationLinks: boolean = true;

  hoveredChartBall: { drawDate: string; num: number } | null = null;

  onChartBallHover(drawDate: string | null, num: number | null) {
    if (drawDate && num !== null) {
      this.hoveredChartBall = { drawDate, num };
    } else {
      this.hoveredChartBall = null;
    }
    this.cdr.markForCheck();
  }

  isBallHighlightedInChart(drawDate: string, num: number): boolean {
    if (!this.hoveredChartBall) return false;
    if (this.hoveredChartBall.drawDate === drawDate && this.hoveredChartBall.num === num)
      return true;

    const links = this.getFilteredTransitionLinks();
    return links.some(
      (l) =>
        (l.fromDrawDate === this.hoveredChartBall?.drawDate &&
          l.fromNumber === this.hoveredChartBall?.num &&
          l.toDrawDate === drawDate &&
          l.toNumber === num) ||
        (l.toDrawDate === this.hoveredChartBall?.drawDate &&
          l.toNumber === this.hoveredChartBall?.num &&
          l.fromDrawDate === drawDate &&
          l.fromNumber === num),
    );
  }

  getFilteredTransitionLinks(): SequenceTransitionLink[] {
    const data = this.linkageChartData();
    if (!data?.transitionLinks) return [];
    return data.transitionLinks.filter((l) => {
      if (l.type === 'REPEAT' && !this.showRepeatLinks) return false;
      if (l.type === 'DRIFT' && !this.showDriftLinks) return false;
      if (l.type === 'SAME_TAIL' && !this.showSameTailLinks) return false;
      if (l.type === 'SPECIAL_MIGRATION' && !this.showSpecialMigrationLinks) return false;
      return true;
    });
  }

  getLinksForStep(fromDrawDate: string, toDrawDate: string): SequenceTransitionLink[] {
    const all = this.getFilteredTransitionLinks();
    return all.filter((l) => l.fromDrawDate === fromDrawDate && l.toDrawDate === toDrawDate);
  }
}
