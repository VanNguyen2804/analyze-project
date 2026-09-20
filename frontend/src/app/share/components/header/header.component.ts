import { Component, OnInit } from '@angular/core';
import { AnalyzeService } from 'src/app/core/services/analyze.service';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
})
export class HeaderComponent implements OnInit {
  currentCategory: string = 'MEGA';

  // Inject Service vào
  constructor(private analyzeService: AnalyzeService) {}

  ngOnInit() {
    this.autoSelectCategoryByDay();
  }

  autoSelectCategoryByDay() {
    const today = new Date().getDay(); 
    
    // POWER: T3 (2), T5 (4), T7 (6)
    if (today === 2 || today === 4 || today === 6) {
      this.selectCategory('POWER');
    } else {
      this.selectCategory('MEGA');
    }
  }

  selectCategory(category: string) {
    this.currentCategory = category;
    
    // Gửi tín hiệu thay đổi danh mục qua Service
    this.analyzeService.setCategory(category);
  }
}