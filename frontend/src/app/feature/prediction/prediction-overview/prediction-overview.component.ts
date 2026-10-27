import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AlgorithmResultItem } from '@app/core/models/prediction-payload.model';

type Metric = 'frequency' | 'drawGap';

@Component({
  selector: 'app-prediction-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, RouterLink],
  templateUrl: './prediction-overview.component.html',
  styleUrl: './prediction-overview.component.css',
})
export class PredictionOverviewComponent {
  readonly primary = input.required<AlgorithmResultItem>();
  readonly secondary = input.required<AlgorithmResultItem>();
  readonly combined = input.required<AlgorithmResultItem>();
  readonly category = input.required<string>();
  readonly historyCount = input(0);
  readonly copied = output<{ numbers: number[]; special?: number }>();
  readonly activeModel = signal(0);
  readonly metric = signal<Metric>('frequency');
  readonly focusedNumber = signal<number | null>(null);
  readonly models = computed(() => [this.primary(), this.secondary(), this.combined()]);
  readonly activeResult = computed(() => this.models()[this.activeModel()]);
  readonly rows = computed(() =>
    [...(this.activeResult().details ?? [])].sort(
      (a, b) => (b[this.metric()] ?? -1) - (a[this.metric()] ?? -1) || a.number - b.number,
    ),
  );
  readonly maxValue = computed(() =>
    Math.max(1, ...this.rows().map((row) => row[this.metric()] ?? 0)),
  );
  readonly focusedDetail = computed<
    NonNullable<AlgorithmResultItem['details']>[number] | undefined
  >(() => this.rows().find((row) => row.number === this.focusedNumber()) ?? this.rows()[0]);
  readonly union = computed(() =>
    [...new Set(this.models().flatMap((model) => model.numbers))].sort((a, b) => a - b),
  );
  readonly shared = computed(() =>
    this.primary().numbers.filter((n) => this.secondary().numbers.includes(n)),
  );
  selectModel(index: number) {
    this.activeModel.set(index);
    this.focusedNumber.set(null);
  }
  width(value: number | undefined) {
    return Math.max(0, Math.min(100, ((value ?? 0) / this.maxValue()) * 100));
  }
  label(index: number) {
    return ['Thuật toán 1', 'Thuật toán 2', 'Kết hợp'][index];
  }
  format(number: number) {
    return String(number).padStart(2, '0');
  }
  copy(model: AlgorithmResultItem) {
    this.copied.emit({
      numbers: model.numbers,
      special: this.category() === 'POWER' ? model.specialNumber : undefined,
    });
  }
}
