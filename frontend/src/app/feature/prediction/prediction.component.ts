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
  
  hoveredNumber?: number = undefined;
  hoveredNumberDetail: any = null;
  hoveredNumberHistory: any[] = [];
  popupStyle: any = { top: '0px', left: '0px' };
  
  category: string = 'MEGA'; 
  private categorySub: Subscription | undefined;

  recentDraws: any[] = [];
  visibleDraws: any[] = [];

  constructor(private analyzeService: AnalyzeService) {}

  ngOnInit() {
    // BehaviorSubject sẽ emit giá trị hiện tại ngay lập tức khi subscribe -> tự động chạy predict() lần đầu tiên vào trang
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      this.category = newCategory;
      this.predict(); 
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
        }, 1000); 
      },
      error: (err) => {
        console.error('Lỗi khi phân tích dữ liệu:', err);
        this.isSpinning = false;
        this.isLoadingHistory = false;
      }
    });
  }

  showPopup(num: number, event: MouseEvent) {
    if (this.payload) {
      this.hoveredNumber = num;
      if (this.payload.selectionReasons) {
        this.hoveredNumberDetail = this.payload.selectionReasons.find(r => r.number === num);
      }
      if (this.payload.recentDraws) {
        this.hoveredNumberHistory = this.payload.recentDraws.filter(draw => 
          (draw.numbers && draw.numbers.includes(num)) || draw.specialNumber === num
        );
      }
      this.updatePopupPosition(event);
    }
  }

  updatePopupPosition(event: MouseEvent) {
    if (!this.hoveredNumber) return;

    let x = event.clientX + 15;
    let y = event.clientY + 15;

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

  hidePopup() {
    this.hoveredNumber = undefined;
    this.hoveredNumberDetail = null;
    this.hoveredNumberHistory = [];
  }

  formatNumber(num: number | undefined): string {
    if (num === undefined) return '--';
    return num < 10 ? '0' + num : num.toString();
  }

  getDayOfWeek(dateString: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    const days = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
    return days[date.getDay()];
  }
}