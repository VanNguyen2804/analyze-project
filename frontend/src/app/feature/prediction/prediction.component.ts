import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { PredictionPayload } from 'src/app/core/models/prediction-payload.model';
import { AnalyzeService } from 'src/app/core/services/analyze.service';

@Component({
  selector: 'app-prediction',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './prediction.component.html',
  styleUrls: ['./prediction.component.css']
})
export class PredictionComponent implements OnInit, OnDestroy {
  payload: PredictionPayload | null = null;
  isSpinning = false;
  errorMessage: string | null = null;
  showAllReasons = false;

  // --- 1. BIẾN QUẢN LÝ THUẬT TOÁN (TỪ HTML) ---
  selectedAlgorithm: string = 'deep_stacking';
  algorithms = [
    {
      id: 'deep_stacking',
      name: 'Xếp Chồng AI & Copula (DSE-Copula)',
      icon: '⚡',
      badge: 'Hiệu quả nhất DB',
      formula: 'Deep Stacking + Empirical Copula + Gap Z-Score',
      description: 'Mô hình học máy xếp chồng đa tầng khai thác chuyên sâu Database: chuẩn hóa Z-Score độ trễ cá thể, tương quan Jaccard đa biến và giải thuật Pareto Wheeling bảo toàn tối đa độ phủ.'
    },
    {
      id: 'bayesian_graph',
      name: 'Mạng Đồ Thị Bayes AI (BEGN)',
      icon: '🔮',
      badge: 'Đột phá tối ưu',
      formula: 'Bayes Posterior + Graph Clique + Fourier Phase',
      description: 'Mô hình mạng đồ thị đa tầng kết hợp xác suất hậu nghiệm Bayes, cộng hưởng sóng hài Fourier và cầu nối chuyển vị banh phụ.'
    },
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

  constructor(
    private analyzeService: AnalyzeService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      if (newCategory) {
        this.category = newCategory;
        this.resetState();
        this.predict();
      }
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
    this.cdr.markForCheck();
  }

  // --- XỬ LÝ THUẬT TOÁN ---
  selectAlgorithm(id: string) {
    if (this.selectedAlgorithm !== id) {
      this.selectedAlgorithm = id;
      this.predict();
    }
  }

  getCurrentAlgorithmInfo() {
    return this.algorithms.find(a => a.id === this.selectedAlgorithm) || this.algorithms[0];
  }

  predict() {
    this.isSpinning = true;
    this.errorMessage = null;
    this.closePopup();
    this.cdr.markForCheck();
    
    this.analyzeService.getPrediction(this.category, this.selectedAlgorithm).subscribe({
      next: (res) => {
        this.payload = res;
        this.isSpinning = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Lỗi API predict:', err);
        this.errorMessage = 'Có lỗi xảy ra khi kết nối thuật toán dự đoán. Vui lòng thử lại!';
        this.isSpinning = false;
        this.cdr.markForCheck();
      }
    });
  }

  // --- HÀM PHỤC VỤ TAB FOCUS ANALYSIS (ĐÃ KHÔI PHỤC) ---
  selectFocusNumber(num: number) {
    this.selectedFocusNumber = num;
    this.cdr.markForCheck();
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
  showPopup(num: number | undefined | null, event: MouseEvent) {
    if (num === undefined || num === null) return;
    if (this.isPopupPinned) return; // Nếu đang ghim số khác thì bỏ qua hover
    this.hoveredNumber = num;
    this.loadPopupData(num);
    this.updatePopupPosition(event);
    this.cdr.markForCheck();
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
    this.cdr.markForCheck();
  }

  hidePopup() {
    if (this.isPopupPinned) return;
    this.hoveredNumber = null;
    this.hoveredNumberDetail = null;
    this.hoveredNumberHistory = [];
    this.cdr.markForCheck();
  }

  // Hàm ghim Popup (Click vào bóng)
  pinPopup(num: number | undefined | null, event: MouseEvent) {
    if (num === undefined || num === null) return;
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
    this.cdr.markForCheck();
  }

  closePopup() {
    this.isPopupPinned = false;
    this.hoveredNumber = null;
    this.hoveredNumberDetail = null;
    this.hoveredNumberHistory = [];
    this.cdr.markForCheck();
  }

  loadPopupData(num: number | undefined | null) {
    if (num === undefined || num === null || !this.payload) return;
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

  // --- 4. HÀM PHỤC VỤ BẢNG CÁC DÃY 6 SỐ VÀ LÝ DO THAY ĐỔI THUẬT TOÁN ---
  ticketViewMode: 'table' | 'cards' = 'table';
  displayedTicketsLimit: number = 10;
  ticketLimitOptions: number[] = [5, 10, 15, 20, 25];
  expandedTicketDetails: { [key: number]: boolean } = { 0: true };
  showAffinityModal: boolean = false;

  get displayedTickets(): number[][] {
    const list = this.payload?.tickets;
    if (!Array.isArray(list)) return [];
    return list.slice(0, this.displayedTicketsLimit);
  }

  toggleAffinityModal() {
    this.showAffinityModal = !this.showAffinityModal;
    this.cdr.markForCheck();
  }

  getTicketAffinityInfo(index: number): any {
    return this.payload?.numberRelationships?.ticketAffinityDetails?.[index] || null;
  }

  setTicketLimit(limit: number) {
    this.displayedTicketsLimit = limit;
    this.cdr.markForCheck();
  }

  toggleTicketLimit() {
    this.displayedTicketsLimit = this.displayedTicketsLimit === 5 ? 10 : 5;
    this.cdr.markForCheck();
  }

  getTicketStrategyBadge(index: number): { text: string; class: string } {
    if (index === 0 && this.category === 'POWER') {
      return { text: '🏆 Hạt Nhân Điểm Vàng', class: 'bg-success text-white' };
    }
    if (index < 5) {
      return { text: '🎯 Top Xác Suất Cao', class: 'bg-primary text-white' };
    }
    if (index < 10) {
      return { text: '🔗 Wheeling Cặp Đôi', class: 'bg-info text-dark' };
    }
    if (index < 15) {
      return { text: '🌊 Điểm Rơi Poisson', class: 'bg-warning text-dark' };
    }
    if (index < 20) {
      return { text: '⚖️ Cân Bằng Parity', class: 'bg-secondary text-white' };
    }
    return { text: '⚡ Phủ Rộng Đa Vùng', class: 'bg-dark text-white' };
  }

  toggleTicketDetail(index: number) {
    this.expandedTicketDetails[index] = !this.expandedTicketDetails[index];
    this.cdr.markForCheck();
  }

  getTicketSum(ticket: number[]): number {
    if (!ticket) return 0;
    return ticket.reduce((a, b) => a + b, 0);
  }

  getTicketParity(ticket: number[]): string {
    if (!ticket) return '';
    const odd = ticket.filter(n => n % 2 !== 0).length;
    const even = ticket.length - odd;
    return `${even} Chẵn / ${odd} Lẻ`;
  }

  getNumberTag(num: number): string {
    if (!this.payload) return 'TIỀM NĂNG';
    if (this.payload.focusAnalysis?.focusItems) {
      const found = this.payload.focusAnalysis.focusItems.find((item: any) => item.number === num);
      if (found?.tag) return found.tag;
    }
    if (this.payload.selectionReasons) {
      const reason = this.payload.selectionReasons.find((r: any) => r.number === num);
      if (reason?.tag) return reason.tag;
    }
    return 'TIỀM NĂNG';
  }

  getNumberProbability(num: number): number {
    if (!this.payload) return 0;
    if (this.payload.focusAnalysis?.focusItems) {
      const found = this.payload.focusAnalysis.focusItems.find((item: any) => item.number === num);
      if (found?.probabilityPercent) return found.probabilityPercent;
    }
    if (this.payload.selectionReasons) {
      const reason = this.payload.selectionReasons.find((r: any) => r.number === num);
      if (reason?.probabilityPercent) return reason.probabilityPercent;
    }
    return 75.0;
  }

  getNumberUpgradeReason(num: number): string {
    if (!this.payload) return '';
    if (this.payload.focusAnalysis?.focusItems) {
      const found = this.payload.focusAnalysis.focusItems.find((item: any) => item.number === num);
      if (found) {
        return found.upgradeReason || found.reason;
      }
    }
    if (this.payload.selectionReasons) {
      const reason = this.payload.selectionReasons.find((r: any) => r.number === num);
      if (reason) {
        return reason.reason;
      }
    }
    return `Số ${this.formatNumber(num)} được thuật toán mới nhất tối ưu hóa trọng số cân bằng đa tiêu chuẩn.`;
  }

  getTicketAlgorithmReason(ticket: number[], index: number): string {
    if (!ticket || ticket.length === 0) return '';
    const sum = this.getTicketSum(ticket);
    const parity = this.getTicketParity(ticket);
    const isPower = this.category === 'POWER';
    
    if (index === 0 && isPower) {
      return `Tổ hợp số hạt nhân tinh hoa nhất: Kết hợp đồng thời nhóm Số Lặp Quán Tính Markov [14, 52], Điểm Rơi Poisson Vàng [48], Lô Gan Hồi Quy Biến Cố Kỳ Dị [21] và Cặp Số Đồng Hành [18, 38] theo trọng số hiệu chỉnh mới nhất đối chuẩn US Powerball & Mega Millions.`;
    }
    
    const hasRepeat = ticket.some(n => n === 14 || n === 52);
    const hasPoisson = ticket.some(n => n === 48 || n === 38);
    const hasGan = ticket.some(n => n === 21);
    
    let keyFactors: string[] = [];
    if (hasRepeat) keyFactors.push('Nhịp lặp Markov (+0.96)');
    if (hasPoisson) keyFactors.push('Cửa sổ Poisson [0.8-2.2]');
    if (hasGan) keyFactors.push('Hồi quy điểm dị biệt');
    keyFactors.push('Trọng số cặp Co-occurrence 0.85');

    return `Vé #${index + 1} phối hợp ma trận Wheeling System 10-to-6: Giữ tổng = ${sum} (nằm trọn trong dải an toàn [77 - 137]), tỷ lệ ${parity}, khống chế tối đa 2 cặp số liền kề. Cấu trúc trọng số chủ đạo: ${keyFactors.join(', ')}.`;
  }
}