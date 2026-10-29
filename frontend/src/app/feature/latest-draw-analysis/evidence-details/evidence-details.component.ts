import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DrawEvidence } from '../../../core/models/draw-report.model';
import { formatAnalysisNumber } from '../reconciliation-format';

@Component({
  selector: 'app-analysis-evidence',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, KeyValuePipe, FormsModule],
  templateUrl: './evidence-details.component.html',
  styleUrl: './evidence-details.component.css',
})
export class EvidenceDetailsComponent {
  readonly evidence = input.required<DrawEvidence>();
  readonly actual = input.required<number[]>();
  readonly filter = signal('all');
  readonly rows = computed(() => {
    const e = this.evidence();
    return e.numberDetails.filter(
      (d) =>
        this.filter() === 'all' ||
        (this.filter() === 'missed' &&
          e.numbers.includes(d.number) &&
          !this.actual().includes(d.number)) ||
        (this.filter() === 'omitted' &&
          !e.numbers.includes(d.number) &&
          this.actual().includes(d.number)),
    );
  });
  readonly formatNumber = formatAnalysisNumber;
}
