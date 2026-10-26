import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  input,
  output,
} from '@angular/core';
import { DatePipe, JsonPipe, NgClass } from '@angular/common';
import { AlgorithmHyperparameter } from '../../../core/models/algorithm.model';
@Component({
  selector: 'app-hyperparameter-history',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, JsonPipe, NgClass],
  templateUrl: './hyperparameter-history.component.html',
  styleUrl: './hyperparameter-history.component.css',
})
export class HyperparameterHistoryComponent {
  readonly hyperparameterHistory = input<AlgorithmHyperparameter[]>([]);
  readonly isLoadingHyperparameters = input(false);
  readonly reload = output<void>();
  readonly activate = output<number>();
  private readonly cdr = inject(ChangeDetectorRef);
  selectedHyperparameterForView: AlgorithmHyperparameter | null = null;

  activeModalTab: 'json' | 'readme' = 'json';

  viewHyperparameterJson(item: AlgorithmHyperparameter, tab: 'json' | 'readme' = 'json'): void {
    this.selectedHyperparameterForView = item;
    this.activeModalTab = tab;
    this.cdr.markForCheck();
  }

  closeHyperparameterModal(): void {
    this.selectedHyperparameterForView = null;
    this.cdr.markForCheck();
  }
}
