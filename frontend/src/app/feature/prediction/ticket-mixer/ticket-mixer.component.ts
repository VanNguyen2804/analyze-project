import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  inject,
  input,
  output,
} from '@angular/core';
import { NgClass } from '@angular/common';
import { timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LotteryCategory } from '../../../core/models/lottery-category.model';
@Component({
  selector: 'app-ticket-mixer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass],
  templateUrl: './ticket-mixer.component.html',
  styleUrl: './ticket-mixer.component.css',
})
export class TicketMixerComponent {
  readonly primaryNumbers = input<number[]>([]);
  readonly numbers = input<number[]>([]);
  readonly category = input<LotteryCategory>('MEGA');
  readonly copied = output<number[]>();
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  formatNumber(num: number | undefined | null): string {
    if (num === undefined || num === null) return '--';
    return num < 10 ? '0' + num : num.toString();
  }
  customMixerNumbers: number[] = [];

  mixerCopySuccess: boolean = false;

  toggleMixerNumber(num: number) {
    const idx = this.customMixerNumbers.indexOf(num);
    if (idx >= 0) {
      this.customMixerNumbers.splice(idx, 1);
    } else {
      if (this.customMixerNumbers.length < 6) {
        this.customMixerNumbers.push(num);
        this.customMixerNumbers.sort((a, b) => a - b);
      }
    }
    this.cdr.markForCheck();
  }

  isMixerNumberSelected(num: number): boolean {
    return this.customMixerNumbers.includes(num);
  }

  clearMixerNumbers() {
    this.customMixerNumbers = [];
    this.cdr.markForCheck();
  }

  fillMixerAuto(sourceTicket?: number[]) {
    if (sourceTicket && sourceTicket.length === 6) {
      this.customMixerNumbers = [...sourceTicket].sort((a, b) => a - b);
    } else {
      const topNums = this.getCuratedMixerPool().slice(0, 6);
      this.customMixerNumbers = [...topNums].sort((a, b) => a - b);
    }
    this.cdr.markForCheck();
  }

  getCuratedMixerPool(): number[] {
    const set = new Set<number>();
    // Thêm các số nguyên bản AI
    this.primaryNumbers()
      .slice(0, 8)
      .forEach((n) => set.add(n));
    // Thêm top 10 từ payload
    this.numbers()
      .slice(0, 10)
      .forEach((n) => set.add(n));
    return Array.from(set).sort((a, b) => a - b);
  }

  getMixerSum(): number {
    return this.customMixerNumbers.reduce((a, b) => a + b, 0);
  }

  getMixerParity(): string {
    const even = this.customMixerNumbers.filter((n) => n % 2 === 0).length;
    const odd = this.customMixerNumbers.length - even;
    return `${even} Chẵn / ${odd} Lẻ`;
  }

  copyMixerTicket() {
    if (this.customMixerNumbers.length < 6) return;
    this.copied.emit([...this.customMixerNumbers]);
    this.mixerCopySuccess = true;
    timer(3000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.mixerCopySuccess = false;
        this.cdr.markForCheck();
      });
  }
}
