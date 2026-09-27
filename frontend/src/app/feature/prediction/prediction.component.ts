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
  errorMessage: string | null = null;
  showAllReasons = false;

  // --- 1. BIẾN QUẢN LÝ THUẬT TOÁN (TỪ HTML) ---
  selectedAlgorithm: string = 'xgboost';
  algorithms = [
    { id: 'xgboost', name: 'AI XGBoost + Wheeling', icon: '🧠', badge: 'Khuyên dùng', formula: 'Tối ưu Z-Score + Trộn vé', description: 'Phân tích tần suất, lô gan và tương tác cặp để lọc 10 số ưu tú.' },
    { id: 'montecarlo', name: 'Monte Carlo 100K', icon: '🎲', badge: 'Mô phỏng', formula: 'Random walk simulation', description: 'Chạy mô phỏng 100.000 lồng cầu ngẫu nhiên.' },
    { id: 'markov', name: 'Mô hình Markov', icon: '🔗', badge: 'Xác suất', formula: 'P(State A -> State B)', description: 'Dự đoán bước nhảy không gian trạng thái giữa các kỳ quay.' }
  ];

  // --- 2. BIẾN QUẢN LÝ TAB & FOCUS ANALYSIS (TỪ HTML) ---
  activeFocusTab: string = 'target3'; 
  selectedFocusNumber: number | null = null;
  scoreSearchTerm: string = '';

  // --- 3. BIẾN QUẢN LÝ POPUP HOVER & GHIM (TỪ HTML) ---
  hoveredNumber: number | null = null;
  hoveredNumberDetail: any = null;
  hoveredNumberHistory: any[] = [];
  isPopupPinned: boolean = false; // Phục vụ class .pinned
  popupStyle: any = { top: '0px', left: '0px' };

  category: string = 'MEGA'; 
  private categorySub: Subscription | undefined;

  constructor(private analyzeService: AnalyzeService) {}

  ngOnInit() {
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      this.category = newCategory;
      this.resetState();
    });
  }

  ngOnDestroy() {
    if (this.categorySub) this.categorySub.unsubscribe();
  }

  resetState() {
    this.payload = null;
    this.errorMessage = null;
    this.showAllReasons = false;
    this.activeFocusTab = 'target3';
    this.selectedFocusNumber = null;
    this.scoreSearchTerm = '';
    this.closePopup();
  }

  // --- XỬ LÝ THUẬT TOÁN ---
  selectAlgorithm(id: string) {
    this.selectedAlgorithm = id;
  }

  getCurrentAlgorithmInfo() {
    return this.algorithms.find(a => a.id === this.selectedAlgorithm) || this.algorithms[0];
  }

  predict() {
    this.isSpinning = true;
    this.errorMessage = null;
    this.closePopup();
    
    this.analyzeService.getPrediction(this.category, this.selectedAlgorithm).subscribe({
      next: (res) => {
        setTimeout(() => {
          this.payload = res;
          this.isSpinning = false;
        }, 1500); 
      },
      error: (err) => {
        console.error('Lỗi API predict:', err);
        this.errorMessage = 'Có lỗi xảy ra khi kết nối thuật toán dự đoán. Vui lòng thử lại!';
        this.isSpinning = false;
      }
    });
  }

  // --- HÀM PHỤC VỤ TAB FOCUS ANALYSIS (ĐÃ KHÔI PHỤC) ---
  selectFocusNumber(num: number) {
    this.selectedFocusNumber = num;
  }

  getTarget3Items(): any[] {
    if (!this.payload?.focusAnalysis?.focusItems) return [];
    const targets = this.category === 'POWER' ? [48, 52, 14] : [31, 45, 14];
    return this.payload.focusAnalysis.focusItems.filter((item: any) => targets.includes(item.number));
  }

  // HÀM BẠN ĐÃ HỎI: Lọc danh sách thẻ tra cứu
  getFilteredScores(): any[] {
    if (!this.payload?.focusAnalysis?.focusItems) return [];
    let items = this.payload.focusAnalysis.focusItems;
    
    if (this.scoreSearchTerm && this.scoreSearchTerm.trim() !== '') {
      items = items.filter((item: any) => item.number.toString().includes(this.scoreSearchTerm.trim()));
    }
    return items;
  }

  getFocusItem(num: number | null): any {
    if (!num || !this.payload?.focusAnalysis?.focusItems) return null;
    return this.payload.focusAnalysis.focusItems.find((item: any) => item.number === num);
  }

  // --- HÀM PHỤC VỤ POPUP & GHIM (ĐÃ KHÔI PHỤC) ---
  showPopup(num: number, event: MouseEvent) {
    if (this.isPopupPinned) return; // Nếu đang ghim số khác thì bỏ qua hover
    this.hoveredNumber = num;
    this.loadPopupData(num);
    this.updatePopupPosition(event);
  }

  updatePopupPosition(event: MouseEvent) {
    if (this.isPopupPinned || !this.hoveredNumber) return; // Đã ghim thì không di chuyển theo chuột nữa
    
    let x = event.clientX + 15;
    let y = event.clientY + 15;
    const popupWidth = 420; // Khớp với CSS .floating-popup width 420px
    const popupHeight = 400;

    if (x + popupWidth > window.innerWidth) x = event.clientX - popupWidth - 15;
    if (y + popupHeight > window.innerHeight) y = event.clientY - popupHeight - 15;

    this.popupStyle = { top: y + 'px', left: x + 'px' };
  }

  hidePopup() {
    if (this.isPopupPinned) return;
    this.hoveredNumber = null;
    this.hoveredNumberDetail = null;
    this.hoveredNumberHistory = [];
  }

  // Hàm ghim Popup (Click vào bóng)
  pinPopup(num: number, event: MouseEvent) {
    event.stopPropagation(); // Ngăn click lan ra ngoài
    this.isPopupPinned = true;
    this.hoveredNumber = num;
    this.loadPopupData(num);
    // Tính lại vị trí ghim 1 lần
    let x = event.clientX + 15;
    let y = event.clientY + 15;
    if (x + 420 > window.innerWidth) x = event.clientX - 420 - 15;
    if (y + 400 > window.innerHeight) y = event.clientY - 400 - 15;
    this.popupStyle = { top: y + 'px', left: x + 'px' };
  }

  closePopup() {
    this.isPopupPinned = false;
    this.hoveredNumber = null;
    this.hoveredNumberDetail = null;
    this.hoveredNumberHistory = [];
  }

  loadPopupData(num: number) {
    if (!this.payload) return;
    if (this.payload.selectionReasons) {
      this.hoveredNumberDetail = this.payload.selectionReasons.find(r => r.number === num);
    }
    if (this.payload.recentDraws) {
      this.hoveredNumberHistory = this.payload.recentDraws.filter(draw => 
        (draw.numbers && draw.numbers.includes(num)) || draw.specialNumber === num
      );
    }
  }

  // --- UTILS ---
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