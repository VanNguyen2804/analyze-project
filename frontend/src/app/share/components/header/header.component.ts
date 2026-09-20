import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AnalyzeService } from '../../../core/services/analyze.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslatePipe],
  templateUrl: './header.component.html'
})
export class HeaderComponent {
  currentCategory: string = 'MEGA';
  currentLang: string = 'vi';

  constructor(
    private analyzeService: AnalyzeService,
    public translate: TranslateService
  ) {
    const savedLang = localStorage.getItem('appLang') || 'vi';
    this.switchLanguage(savedLang);
  }

  selectCategory(category: string) {
    this.currentCategory = category;
    this.analyzeService.setCategory(category);
  }

  switchLanguage(lang: string) {
    this.currentLang = lang;
    this.translate.use(lang);
    localStorage.setItem('appLang', lang);
  }
}
