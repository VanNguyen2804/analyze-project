import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { LabProgress } from '../../../core/models/forecast.model';

@Component({
  selector: 'app-lab-progress',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './lab-progress.component.html',
  styleUrl: './lab-progress.component.css',
})
export class LabProgressComponent {
  readonly progress = input.required<LabProgress>();
  readonly selected = signal(1);
  readonly level = computed(() =>
    this.progress().levels.find((l) => l.threshold === this.selected()),
  );
  readonly recent = computed(() => this.progress().timeline.slice(-10).reverse());
  percent(value: number | null | undefined) {
    return value == null ? 'Chưa có' : `${value.toFixed(1)}%`;
  }
  width(value: number | null | undefined) {
    return Math.min(100, Math.max(0, value ?? 0));
  }
}
