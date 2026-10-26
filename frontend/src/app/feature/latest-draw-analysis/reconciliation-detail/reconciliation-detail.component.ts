import {
  formatAnalysisNumber,
  formatAnalysisMetric,
  nearMissTarget,
  isNearMiss,
} from '../reconciliation-format';
import { ChangeDetectionStrategy, Component, input, output, model } from '@angular/core';
import { NgClass, JsonPipe } from '@angular/common';
import { TicketAffinityDetail } from '../../../core/models/prediction-payload.model';
export type ReconciliationTab =
  | 'aiPredictionJudgment'
  | 'comparison'
  | 'winningBalls'
  | 'algorithmFlaws'
  | 'tuningPlan'
  | 'affinityNetwork';

@Component({
  selector: 'app-reconciliation-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass, JsonPipe],
  templateUrl: './reconciliation-detail.component.html',
  styleUrl: './reconciliation-detail.component.css',
})
export class ReconciliationDetailComponent {
  readonly category = input('MEGA');
  readonly reconciliationData = input<any>(null);
  readonly reconcileAlgorithm = input('temporal_learning');
  readonly isApplyingV150 = input(false);
  readonly currentHyperparameterVersion = input('');
  readonly draw = input<any>(null);
  readonly applyTuning = output<void>();
  readonly activeReconciliationTab = model<ReconciliationTab>('aiPredictionJudgment');

  getAffinityTickets(draw: any): TicketAffinityDetail[] {
    const list = draw?.aiPrediction?.numberRelationships?.ticketAffinityDetails;
    if (!Array.isArray(list)) return [];
    return list.slice(0, 10);
  }

  readonly isNearMiss = isNearMiss;

  readonly getNearMissTarget = nearMissTarget;

  readonly formatMetric = formatAnalysisMetric;

  readonly formatNumber = formatAnalysisNumber;
}
