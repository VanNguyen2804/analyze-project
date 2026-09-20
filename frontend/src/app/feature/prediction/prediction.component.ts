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
  payload: PredictionPayload | null = null;
  isSpinning = false;
  isLoadingHistory = false;
  showAllReasons = false;
  
  // Trạng thái quản lý Popup Hover
  hoveredNumber: number | null = null; // Trạng thái giữ màu xanh lá
  hoveredNumberDetail: any = null;
  hoveredNumberHistory: any[] = []; // Lịch sử các kỳ có mặt số này
  popupStyle: any = { top: '0px', left: '0px' };
  
  category: string = 'MEGA'; 
  private categorySub: Subscription | undefined;

  recentDraws: any[] = [];
  visibleDraws: any[] = [];

  constructor(private analyzeService: AnalyzeService) {}

  ngOnInit() {
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      this.category = newCategory;
      this.payload = null; 
      this.showAllReasons = false;
      this.hidePopup();
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
    this.hidePopup();
    
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

  // Bắt sự kiện chuột đi vào quả bóng
  showPopup(num: number, event: MouseEvent) {
    if (this.payload) {
      this.hoveredNumber = num; // Kích hoạt class xanh lá
      
      // Lấy lý do AI chọn
      if (this.payload.selectionReasons) {
        this.hoveredNumberDetail = this.payload.selectionReasons.find(r => r.number === num);
      }
      
      // Lọc lịch sử 10 ngày gần nhất xem số này ra vào ngày nào
      if (this.payload.recentDraws) {
        this.hoveredNumberHistory = this.payload.recentDraws.filter(draw => 
          (draw.numbers && draw.numbers.includes(num)) || draw.specialNumber === num
        );
      }

      this.updatePopupPosition(event);
    }
  }

  // Cập nhật vị trí bám theo trỏ chuột và chống tràn viền
  updatePopupPosition(event: MouseEvent) {
    if (!this.hoveredNumber) return;

    let x = event.clientX + 15;
    let y = event.clientY + 15;

    // Kích thước ước tính của popup (đã tăng chiều cao để chứa lịch sử)
    const popupWidth = 350;
    const popupHeight = 350;

    if (x + popupWidth > window.innerWidth) {
      x = event.clientX - popupWidth - 15;
    }
    if (y + popupHeight > window.innerHeight) {
      y = event.clientY - popupHeight - 15;
    }

    this.popupStyle = {
      top: y + 'px',
      left: x + 'px'
    };
  }

  // Ẩn Popup khi chuột rời đi
  hidePopup() {
    this.hoveredNumber = null;
    this.hoveredNumberDetail = null;
    this.hoveredNumberHistory = [];
  }

  formatNumber(num: number | undefined | null): string {
    if (num === undefined || num === null) return '--';
    return num < 10 ? '0' + num : num.toString();
  }

  getDayOfWeek(dateString: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    const days = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
    return days[date.getDay()];
  }
}