import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DecimalPipe, KeyValuePipe } from '@angular/common';
import { AnalyzedTicketGroup } from '../../../core/models/draw-report.model';
import { formatAnalysisNumber } from '../reconciliation-format';

@Component({
  selector: 'app-analysis-ticket-group',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, KeyValuePipe],
  templateUrl: './ticket-group.component.html',
  styleUrl: './ticket-group.component.css',
})
export class AnalysisTicketGroupComponent {
  readonly group = input.required<AnalyzedTicketGroup>();
  readonly formatNumber = formatAnalysisNumber;
}
