import { Component, OnInit } from '@angular/core';
// Import SharedService hoặc Service bạn đang dùng để quản lý State giữa các Component

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html'
})
export class HeaderComponent implements OnInit {
  currentCategory: string = 'MEGA';

  ngOnInit() {
    this.autoSelectCategoryByDay();
  }

  autoSelectCategoryByDay() {
    // Lấy ngày hiện tại trong tuần (0: CN, 1: T2, 2: T3, 3: T4, 4: T5, 5: T6, 6: T7)
    const today = new Date().getDay(); 
    
    // POWER quay vào Thứ 3 (2), Thứ 5 (4), Thứ 7 (6)
    if (today === 2 || today === 4 || today === 6) {
      this.selectCategory('POWER');
    } else {
      // MEGA quay vào Thứ 4 (3), Thứ 6 (5), Chủ nhật (0)
      // Mặc định Thứ 2 (1 - không có lịch quay) cũng sẽ chọn MEGA
      this.selectCategory('MEGA');
    }
  }

  selectCategory(category: string) {
    this.currentCategory = category;
    
    // TODO: Bắn sự kiện (emit) thay đổi category này tới AnalyzeService 
    // để PredictionComponent và các Component khác tự động load lại dữ liệu
    // Ví dụ: this.sharedService.setCategory(category);
  }
}