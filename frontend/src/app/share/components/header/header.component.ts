import { Component } from '@angular/core';
import { TranslateService } from '@ngx-translate/core'; // Import Service dịch thuật
import { AnalyzeService } from 'src/app/core/services/analyze.service';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent {
  currentCategory: string = 'MEGA';
  currentLang: string = 'vi'; // Biến lưu ngôn ngữ hiện tại đang chọn

  constructor(
    private analyzeService: AnalyzeService,
    public translate: TranslateService // Inject vào constructor
  ) {
    // Khôi phục ngôn ngữ đã chọn từ LocalStorage (nếu có)
    const savedLang = localStorage.getItem('appLang') || 'vi';
    this.switchLanguage(savedLang);
  }

  selectCategory(category: string) {
    this.currentCategory = category;
    this.analyzeService.setCategory(category);
  }

  // Hàm chuyển đổi ngôn ngữ
  switchLanguage(lang: string) {
    this.currentLang = lang;
    this.translate.use(lang);
    localStorage.setItem('appLang', lang); // Lưu vào bộ nhớ trình duyệt
  }
}