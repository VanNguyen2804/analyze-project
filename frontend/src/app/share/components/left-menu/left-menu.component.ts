import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, distinctUntilChanged, of, switchMap } from 'rxjs';
import { CategoryService } from '../../../core/services/category.service';
import { AnalyzeService } from '../../../core/services/analyze.service';

@Component({
  selector: 'app-left-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './left-menu.component.html',
  styleUrl: './left-menu.component.css',
})
export class LeftMenuComponent implements OnInit {
  private readonly categories = inject(CategoryService);
  private readonly analyze = inject(AnalyzeService);
  private readonly destroyRef = inject(DestroyRef);
  readonly latestDrawBadge = signal('Kỳ mới nhất');

  ngOnInit(): void {
    this.categories.category$
      .pipe(
        distinctUntilChanged(),
        switchMap((category) =>
          this.analyze.getLatestDraw(category).pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        const latest = result;
        const numbers = latest?.numbers ?? [];
        const summary = numbers.length
          ? numbers.length + ' số [' + numbers[0] + '..' + numbers[numbers.length - 1] + ']'
          : 'Chưa có kết quả';
        this.latestDrawBadge.set(
          latest?.specialNumber ? summary + ' + Phụ ' + latest.specialNumber : summary,
        );
      });
  }
}
