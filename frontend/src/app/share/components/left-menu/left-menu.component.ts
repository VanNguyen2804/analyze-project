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
import { catchError, of, switchMap } from 'rxjs';
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
  readonly latestDrawBadge = signal('K? m?i nh?t');

  ngOnInit(): void {
    this.categories.category$
      .pipe(
        switchMap((category) =>
          this.analyze
            .get5DrawsReconciliation(category, 'deep_stacking', 1)
            .pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        const latest = result?.draws?.[0];
        const numbers: number[] = latest?.officialNumbers ?? [];
        const summary = numbers.length
          ? numbers.length + ' s? [' + numbers[0] + '..' + numbers[numbers.length - 1] + ']'
          : 'Ch?a c? k?t qu?';
        this.latestDrawBadge.set(
          latest?.officialSpecial ? summary + ' + Ph? ' + latest.officialSpecial : summary,
        );
      });
  }
}
