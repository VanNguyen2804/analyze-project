import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';

@Component({
  selector: 'app-expanded-tickets',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './expanded-tickets.component.html',
  styleUrl: './expanded-tickets.component.css',
})
export class ExpandedTicketsComponent {
  readonly tickets = input<number[][]>([]);
  readonly experimental = input<number[][]>([]);
  readonly category = input('MEGA');
  readonly specialNumber = input<number | null>(null);
  readonly copied = output<{ numbers: number[]; special?: number }>();
  readonly expanded = signal(false);
  readonly useExperimental = signal(false);
  readonly limit = signal(5);
  readonly chosen = computed(() =>
    this.useExperimental() && this.experimental().length ? this.experimental() : this.tickets(),
  );
  readonly displayed = computed(() => this.chosen().slice(0, this.limit()));
  readonly coverage = computed(() => new Set(this.displayed().flat()).size);
  format(n: number) {
    return String(n).padStart(2, '0');
  }
  setLimit(event: Event) {
    this.limit.set(Number((event.target as HTMLSelectElement).value));
  }
  copy(numbers: number[]) {
    const special = this.specialNumber();
    this.copied.emit({
      numbers,
      special:
        this.category() === 'POWER' && special != null && !numbers.includes(special)
          ? special
          : undefined,
    });
  }
}
