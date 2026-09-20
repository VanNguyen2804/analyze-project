import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { PredictionPayload } from 'src/app/core/models/prediction-payload.model';
import { AnalyzeService } from 'src/app/core/services/analyze.service';

@Component({
  selector: 'app-prediction',
  templateUrl: './prediction.component.html',
  styleUrls: ['./prediction.component.css']
})
export class PredictionComponent implements OnInit, OnDestroy {
  // Trạng thái dữ liệu API
  payload: PredictionPayload | null = null;
  
  // Trạng thái loading
  isSpinning = false;
  isLoadingHistory = false;

  // Trạng thái hiển thị giao diện
  showAllReasons = false;
  
  // Biến lưu trữ chi tiết con số đang được chọn để hiển thị lên Popup Modal
  selectedNumberDetail: any = null;
  
  // Biến lưu danh mục hiện tại (từ Header)
  category: string = 'MEGA'; 
  private categorySub: Subscription | undefined;

  // Quản lý dữ liệu lịch sử
  recentDraws: any[] = [];
  visibleDraws: any[] = [];

  constructor(private analyzeService: AnalyzeService) {}

  ngOnInit() {
    // Lắng nghe sự thay đổi Category từ Header thông qua BehaviorSubject
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      this.category = newCategory;
      
      // Reset lại toàn bộ giao diện khi người dùng chuyển danh mục khác
      this.payload = null; 
      this.showAllReasons = false;
      this.selectedNumberDetail = null;
      this.recentDraws = [];
      this.visibleDraws = [];
    });
  }

  ngOnDestroy() {
    if (this.categorySub) {
      this.categorySub.unsubscribe();
    }
  }

  predict() {
    this.isSpinning = true;
    this.isLoadingHistory = true;
    this.payload = null;
    this.showAllReasons = false;
    this.selectedNumberDetail = null;
    
    this.analyzeService.getPrediction(this.category).subscribe({
      next: (res) => {
        setTimeout(() => {
          this.payload = res;
          
          if (res && res.recentDraws) {
            this.recentDraws = res.recentDraws;
            this.visibleDraws = this.recentDraws.slice(0, 10);
          }
          
          this.isSpinning = false;
          this.isLoadingHistory = false;
        }, 1500); 
      },
      error: (err) => {
        console.error('Lỗi khi phân tích dữ liệu:', err);
        this.isSpinning = false;
        this.isLoadingHistory = false;
      }
    });
  }

  // Ánh xạ dữ liệu khi người dùng bấm vào một quả bóng để mở Modal
  openNumberDetail(num: number) {
    if (this.payload && this.payload.selectionReasons) {
      this.selectedNumberDetail = this.payload.selectionReasons.find(r => r.number === num);
    }
  }

  // Định dạng số (thêm số 0 phía trước nếu bé hơn 10)
  formatNumber(num: number | undefined): string {
    if (num === undefined || num === null) return '--';
    return num < 10 ? '0' + num : num.toString();
  }

  // Chuyển đổi chuỗi ngày tháng sang định dạng Thứ trong tuần
  getDayOfWeek(dateString: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    const days = ['Chủ nhật', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];
    return days[date.getDay()];
  }

  showMoreHistory() {
    this.visibleDraws = [...this.recentDraws];
  }

  collapseHistory() {
    this.visibleDraws = this.recentDraws.slice(0, 10);
  }
}