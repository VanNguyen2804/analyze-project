import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { DrawEvaluation } from '../../../core/models/draw-report.model';

@Component({
  selector: 'app-draw-reconciliation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  templateUrl: './draw-reconciliation.component.html',
  styleUrl: './draw-reconciliation.component.css',
})
export class DrawReconciliationComponent {
  readonly evaluation = input<DrawEvaluation[]>([]);
}
