import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { AnalyzeService } from '../../../core/services/analyze.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslatePipe],
  templateUrl: './header.component.html'
})
export class HeaderComponent implements OnInit, OnDestroy {
  currentCategory: string = 'MEGA';
  currentLang: string = localStorage.getItem('appLang') || 'vi';
  private catSub?: Subscription;

  constructor(
    private analyzeService: AnalyzeService,
    public translate: TranslateService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.translate.use(this.currentLang);
    this.catSub = this.analyzeService.currentCategory$.subscribe(category => {
      if (category && this.currentCategory !== category) {
        this.currentCategory = category;
        this.cdr.markForCheck();
      }
    });
  }

  ngOnDestroy(): void {
    this.catSub?.unsubscribe();
  }

  selectCategory(category: string) {
    this.currentCategory = category;
    this.analyzeService.setCategory(category);
    this.cdr.markForCheck();
  }

  switchLanguage(lang: string) {
    this.currentLang = lang;
    this.translate.use(lang);
    localStorage.setItem('appLang', lang);
    this.cdr.markForCheck();
  }
}
