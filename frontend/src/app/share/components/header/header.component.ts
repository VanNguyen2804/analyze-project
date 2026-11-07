import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { NavigationService } from '../../../core/services/navigation.service';

@Component({
  selector: 'app-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
})
export class HeaderComponent implements OnInit {
  readonly navigation = inject(NavigationService);
  private readonly translate = inject(TranslateService);
  readonly currentLang = signal(localStorage.getItem('appLang') || 'vi');

  ngOnInit(): void {
    this.translate.use(this.currentLang());
  }
  languageChanged(event: Event): void {
    this.switchLanguage((event.target as HTMLSelectElement).value);
  }
  switchLanguage(language: string): void {
    this.currentLang.set(language);
    this.translate.use(language);
    localStorage.setItem('appLang', language);
  }
}
