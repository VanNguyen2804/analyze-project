import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NgClass, UpperCasePipe } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CategoryService } from '../../../core/services/category.service';
import { LotteryCategory } from '../../../core/models/lottery-category.model';

@Component({
  selector: 'app-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass, UpperCasePipe, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
})
export class HeaderComponent implements OnInit {
  private readonly categories = inject(CategoryService);
  private readonly translate = inject(TranslateService);
  readonly currentCategory = this.categories.category;
  readonly currentLang = signal(localStorage.getItem('appLang') || 'vi');

  ngOnInit(): void {
    this.translate.use(this.currentLang());
  }
  selectCategory(category: LotteryCategory): void {
    this.categories.setCategory(category);
  }
  switchLanguage(language: string): void {
    this.currentLang.set(language);
    this.translate.use(language);
    localStorage.setItem('appLang', language);
  }
}
