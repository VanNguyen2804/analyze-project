import { Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BehaviorSubject } from 'rxjs';
import { LotteryCategory, normalizeCategory } from '../models/lottery-category.model';

/** One category source for the header, routes and prediction features. */
@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly categorySubject = new BehaviorSubject<LotteryCategory>('MEGA');
  readonly category$ = this.categorySubject.asObservable();
  readonly category = toSignal(this.category$, { requireSync: true });

  get currentCategory(): LotteryCategory {
    return this.category();
  }

  setCategory(value: string): void {
    const category = normalizeCategory(value);
    if (category !== this.category()) this.categorySubject.next(category);
  }
}
