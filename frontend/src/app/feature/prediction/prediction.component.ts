import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { PredictionPayload } from 'src/app/core/models/prediction-payload.model';
import { AnalyzeService } from 'src/app/core/services/analyze.service';

export interface AlgorithmOption {
  id: string;
  name: string;
  badge: string;
  description: string;
  icon: string;
  formula: string;
}

@Component({
  selector: 'app-prediction',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './prediction.component.html',
  styleUrls: ['./prediction.component.css']
})
export class PredictionComponent implements OnInit, OnDestroy {
  payload: PredictionPayload | null = null;
  isSpinning = false;
  isLoadingHistory = false;
  showAllReasons = false;
  errorMessage = '';
  
  hoveredNumber?: number = undefined;
  hoveredNumberDetail: any = null;
  hoveredNumberHistory: any[] = [];
  popupStyle: any = { top: '0px', left: '0px' };
  
  category: string = 'MEGA'; 
  private categorySub: Subscription | undefined;

  selectedAlgorithm: string = 'xgboost';

  algorithms: AlgorithmOption[] = [
    {
      id: 'xgboost',
      name: 'XGBoost AI',
      badge: 'Đa biến kết hợp',
      description: 'Học máy Gradient Boosted kết hợp Quán tính (Momentum) + Lô Gan chu kỳ + Ma trận cặp số đồng hành.',
      icon: '⚡',
      formula: 'Gradient Boost & Momentum'
    },
    {
      id: 'monte_carlo',
      name: 'Monte Carlo 100K',
      badge: 'Mô phỏng 100.000 kịch bản',
      description: 'Mô phỏng 100.000 lượt quay có trọng số xác suất, đối chuẩn các giải thưởng lớn toàn cầu (Powerball & Mega Millions).',
      icon: '🎲',
      formula: 'Expected Value (EV) Convergence'
    },
    {
      id: 'markov_chain',
      name: 'Chuỗi Markov',
      badge: 'Ma trận chuyển dịch',
      description: 'Tính xác suất chuyển dịch có điều kiện P(St | St-1) từ kết quả kỳ mở thưởng gần nhất, dự báo bước nhảy tiếp theo.',
      icon: '🔗',
      formula: '1st-Order Transition Probability'
    },
    {
      id: 'poisson_gap',
      name: 'Poisson & Lô Gan',
      badge: 'Hồi quy phân phối',
      description: 'Mô hình phân phối Poisson phát hiện độ trễ tích lũy cực hạn và kích hoạt điểm rơi hồi quy (Mean Reversion).',
      icon: '🎯',
      formula: 'Poisson Process Mean Reversion'
    },
    {
      id: 'delta_wheeling',
      name: 'Delta & Wheeling',
      badge: 'Cân bằng khoảng cách',
      description: 'Phân tích khoảng cách Delta lý tưởng giữa các số liền kề, lọc bẫy số quá nóng và bọc lót qua ma trận Wheeling 10-to-6.',
      icon: '🛡️',
      formula: 'Delta Distance Spacing & Wheel'
    }
  ];

  recentDraws: any[] = [];
  visibleDraws: any[] = [];

  constructor(
    private analyzeService: AnalyzeService,
    private cdr: ChangeDetectorRef
  ) {}

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

  selectAlgorithm(algId: string) {
    if (this.selectedAlgorithm === algId && !this.isSpinning) {
      return;
    }
    this.selectedAlgorithm = algId;
    this.predict();
  }

  getCurrentAlgorithmInfo(): AlgorithmOption {
    return this.algorithms.find(a => a.id === this.selectedAlgorithm) || this.algorithms[0];
  }

  predict() {
    this.isSpinning = true;
    this.isLoadingHistory = true;
    this.errorMessage = '';
    this.payload = null;
    this.showAllReasons = false;
    this.hidePopup();
    this.cdr.detectChanges();
    
    this.analyzeService.getPrediction(this.category, this.selectedAlgorithm).subscribe({
      next: (res) => {
        setTimeout(() => {
          this.payload = res;
          
          if (res && res.recentDraws) {
            this.recentDraws = res.recentDraws;
            this.visibleDraws = this.recentDraws.slice(0, 10);
          }
          
          this.isSpinning = false;
          this.isLoadingHistory = false;
          this.cdr.detectChanges();
        }, 300); 
      },
      error: (err) => {
        console.error('Lỗi khi phân tích dữ liệu:', err);
        this.errorMessage = 'Không thể kết nối đến máy chủ phân tích. Vui lòng thử lại sau.';
        this.isSpinning = false;
        this.isLoadingHistory = false;
        this.cdr.detectChanges();
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

  // --- MỤC ĐỐI SOÁT & CHI TIẾT ĐIỂM SỐ CÁC SỐ 48, 52, 14 ---
  selectedFocusNumber: number = 48;
  activeFocusTab: 'target3' | 'fullDraw' | 'allScores' = 'target3';
  scoreSearchTerm: string = '';

  selectFocusNumber(num: number) {
    this.selectedFocusNumber = num;
  }

  getFocusItem(num: number): any {
    if (!this.payload) return null;
    if (this.payload.focusAnalysis && this.payload.focusAnalysis.focusItems) {
      const found = this.payload.focusAnalysis.focusItems.find(f => f.number === num);
      if (found) return found;
    }
    if (this.payload.allNumberScores) {
      return this.payload.allNumberScores.find(s => s.number === num) || null;
    }
    return null;
  }

  getTarget3Items(): any[] {
    const targetNumbers = [48, 52, 14];
    return targetNumbers.map(n => this.getFocusItem(n)).filter(item => item !== null);
  }

  getFilteredScores(): any[] {
    if (!this.payload || !this.payload.allNumberScores) return [];
    if (!this.scoreSearchTerm.trim()) {
      return this.payload.allNumberScores;
    }
    const term = this.scoreSearchTerm.trim().toLowerCase();
    const termNum = parseInt(term, 10);
    return this.payload.allNumberScores.filter(s => 
      s.number === termNum ||
      s.number.toString().includes(term) ||
      s.tag.toLowerCase().includes(term) ||
      s.title.toLowerCase().includes(term)
    );
  }
}