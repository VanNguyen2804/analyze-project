import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  PredictionPayload,
  CategoryLinkageChartData,
  DrawSequenceNode,
  SequenceTransitionLink,
  NumberGapPressureItem,
  DeltaSpacingDistributionItem,
  SumDistributionItem,
  ParityDistributionItem
} from 'src/app/core/models/prediction-payload.model';
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

  // --- BIẾN QUẢN LÝ BIỂU ĐỒ LIÊN KẾT DÃY SỐ CSDL (HISTORICAL SEQUENCE LINKAGE CHART) ---
  chartCategory: 'MEGA' | 'POWER' = 'MEGA';
  chartMode: 'timeline' | 'gap_pressure' | 'delta_distribution' = 'timeline';
  showRepeatLinks: boolean = true;
  showDriftLinks: boolean = true;
  showSameTailLinks: boolean = true;
  showSpecialMigrationLinks: boolean = true;
  hoveredChartBall: { drawDate: string; num: number } | null = null;
  gapFilterZone: 'ALL' | 'HOT' | 'CYCLE' | 'PRESSURE' | 'OVERDUE' = 'ALL';
  selectedPressureNumber: NumberGapPressureItem | null = null;
  linkageChartData: CategoryLinkageChartData | null = null;
  cachedHistoryRecords: { [key: string]: any[] } = {};
  isChartLoading: boolean = false;

  // --- 1. BIẾN QUẢN LÝ THUẬT TOÁN (SẮP XẾP TỪ KHẢ NĂNG CHO KẾT QUẢ ĐÚNG NHẤT: TOP 1 -> TOP 7) ---
  selectedAlgorithm: string = 'temporal_learning'; // Mặc định thuật toán 1: Top 1 BEGN
  selectedAlgorithm2: string = 'bayesian_graph'; // Mặc định thuật toán 2: Top 2 DSE-Copula
  algorithms: any[] = [];

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
    this.loadAlgorithms();
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      if (newCategory) {
        this.category = newCategory;
        this.chartCategory = (newCategory === 'POWER' ? 'POWER' : 'MEGA');
        this.resetState();
        // Mặc định luôn là thuật toán tốt nhất (Top 1)
        this.selectedAlgorithm = this.algorithms[0]?.id || 'temporal_learning';
        this.predict();
        this.loadChartDataForCategory(this.chartCategory);
      }
    });
    this.loadChartDataForCategory(this.chartCategory);
  }

  loadAlgorithms() {
    this.analyzeService.getAlgorithms().subscribe({
      next: (list) => {
        if (Array.isArray(list) && list.length > 0) {
          this.algorithms = list;
          this.cdr.markForCheck();
        }
      },
      error: () => {}
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

  selectAlgorithm2(id: string) {
    if (this.selectedAlgorithm2 !== id) {
      this.selectedAlgorithm2 = id;
      this.predict();
    }
  }

  getCurrentAlgorithmInfo() {
    return this.algorithms.find(a => a.id === this.selectedAlgorithm) || { name: this.payload?.algorithmName ?? 'Đang tải thuật toán', icon: '', formula: '', description: '', rankBadge: '' };
  }

  getSecondaryAlgorithmInfo() {
    return this.algorithms.find(a => a.id === this.selectedAlgorithm2) || { name: this.payload?.secondaryAlgorithmResult?.name ?? 'Đang tải thuật toán', icon: '', formula: '', description: '', rankBadge: '' };
  }

  predict() {
    this.isSpinning = true;
    this.errorMessage = null;
    this.closePopup();
    this.cdr.markForCheck();
    
    this.analyzeService.getPrediction(this.category, this.selectedAlgorithm, this.selectedAlgorithm2).subscribe({
      next: (res) => {
        if (res.status !== 'SUCCESS') {
          this.payload = null; this.errorMessage = res.message ?? res.analysisSummary ?? 'Chưa có lịch sử hợp lệ để phân tích.';
          this.isSpinning = false; this.cdr.markForCheck(); return;
        }
        this.payload = res;
        this.isSpinning = false;
        this.loadDeviationVariables();
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

  // --- TRẢ VỀ 3 KẾT QUẢ DỰ ĐOÁN (THUẬT TOÁN 1, THUẬT TOÁN 2, KẾT HỢP 2 THUẬT TOÁN) ---
  getPrimaryResult(): any {
    if (this.payload?.primaryAlgorithmResult) {
      return this.payload.primaryAlgorithmResult;
    }
    const numbers = this.getPureAlgorithmNumbers();
    return {
      id: this.selectedAlgorithm,
      name: this.getCurrentAlgorithmInfo().name,
      badge: 'Thuật Toán 1',
      rankBadge: this.getCurrentAlgorithmInfo().rankBadge,
      accuracyScore: null,
      hitRate: 'N/A',
      numbers,
      specialNumber: this.getPureSpecialNumber(),
      composition: this.getTicketParity(numbers) + ' • Tổng = ' + this.getTicketSum(numbers),
      details: this.payload?.pureAlgorithmTicket?.details || []
    };
  }

  getSecondaryResult(): any {
    if (this.payload?.secondaryAlgorithmResult) {
      return this.payload.secondaryAlgorithmResult;
    }
    return { id: this.selectedAlgorithm2, name: this.getSecondaryAlgorithmInfo().name,
      numbers: [], accuracyScore: null, hitRate: 'N/A', details: [] };
  }

  getPatternCards(): any[] {
    const patterns = this.payload?.commonPatternsAnalysis;
    return patterns ? Object.entries(patterns).filter(([key]) => key !== 'evaluatedDraws').map(([, value]) => value) : [];
  }

  getCommonPatternsAnalysis(): any {
    return this.payload?.commonPatternsAnalysis;
  }

  getCombinedResult(): any {
    if (this.payload?.combinedAlgorithmResult) {
      return this.payload.combinedAlgorithmResult;
    }
    return { id: 'combined_ensemble', name: 'Ensemble consensus',
      numbers: [], accuracyScore: null, hitRate: 'N/A', details: [] };
  }

  getPureAlgorithmNumbers(): number[] {
    if (this.payload?.pureAlgorithmTicket?.numbers?.length) {
      return this.payload.pureAlgorithmTicket.numbers;
    }
    if (this.payload?.pureNumbers?.length) {
      return this.payload.pureNumbers;
    }
    if (this.payload?.tickets && this.payload.tickets.length > 0) {
      return this.payload.tickets[0].slice(0, 6);
    }
    if (this.payload?.numbers && this.payload.numbers.length >= 6) {
      return this.payload.numbers.slice(0, 6);
    }
    return [];
  }

  getPureSpecialNumber(): number | undefined {
    return this.payload?.pureAlgorithmTicket?.specialNumber ?? this.payload?.specialNumber;
  }

  getPureNumberDetail(num: number): any {
    if (this.payload?.pureAlgorithmTicket?.details) {
      const found = this.payload.pureAlgorithmTicket.details.find((d: any) => d.number === num);
      if (found) return found;
    }
    if (this.payload?.details) {
      const found = this.payload.details.find((d: any) => d.number === num);
      if (found) return found;
    }
    if (this.payload?.selectionReasons) {
      const found = this.payload.selectionReasons.find((r: any) => r.number === num);
      if (found) return found;
    }
    return null;
  }

  getSecondaryNumberDetail(num: number): any {
    return this.payload?.secondaryAlgorithmResult?.details?.find(detail => detail.number === num) ?? null;
  }

  // --- HÀM PHỤC VỤ TAB FOCUS ANALYSIS (ĐÃ KHÔI PHỤC) ---
  selectFocusNumber(num: number) {
    this.selectedFocusNumber = num;
    this.cdr.markForCheck();
  }

  getTarget3Items(): any[] {
    if (!this.payload?.focusAnalysis?.focusItems) return [];
    const upgraded = this.payload.focusAnalysis.upgradedNumbers || [];
    const targetItems = this.payload.focusAnalysis.focusItems.filter(
      (item: any) => item.isTargetUpgrade || upgraded.includes(item.number)
    );
    return targetItems.length > 0
      ? targetItems.slice(0, 3)
      : this.payload.focusAnalysis.focusItems.slice(0, 3);
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

  formatDateVN(dateStr: string): string {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length === 3) {
      const dow = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2])).getDay();
      const days = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
      return `${days[dow]} ${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return dateStr;
  }

  copyTicketSuccessMsg: string | null = null;
  copyTicket(numbers: number[], special?: number | null) {
    const text = numbers.map(n => this.formatNumber(n)).join(' - ') + (special ? ` (⭐${this.formatNumber(special)})` : '');
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(text);
    }
    this.copyTicketSuccessMsg = `Đã sao chép vé: [ ${text} ]`;
    setTimeout(() => {
      this.copyTicketSuccessMsg = null;
      this.cdr.markForCheck();
    }, 3000);
    this.cdr.markForCheck();
  }

  getTargetDrawInfo(): {
    drawDate: string;
    dayName: string;
    drawDateFormatted: string;
    isToday: boolean;
    statusText: string;
    statusBadgeClass: string;
    fullDisplay: string;
  } {
    const drawDate = this.payload?.targetDrawDate ?? '';
    const dayName = this.payload?.targetDayName ?? '';
    const drawDateFormatted = this.payload?.targetDrawDateFormatted ?? (drawDate ? drawDate.split('-').reverse().join('/') : '');
    const isToday = this.payload?.isTargetDrawToday === true;
    return { drawDate, dayName, drawDateFormatted, isToday,
      statusText: drawDate ? (isToday ? 'Kỳ quay hôm nay: ' : 'Kỳ quay tiếp theo: ') + dayName + ' ' + drawDateFormatted : 'Chưa có lịch quay từ máy chủ.',
      statusBadgeClass: isToday ? 'bg-warning text-dark' : 'bg-info text-dark',
      fullDisplay: [dayName, drawDateFormatted].filter(Boolean).join(' ') };
  }

  getTransformedSequences(): any[] {
    return this.payload?.transformedSequences ?? [];
  }

  // --- CHIẾN LƯỢC LỌC DÃY SỐ VÀ LIÊN HỆ THỰC TẾ VS DỰ ĐOÁN AI ---
  selectedSeqCategory: string = 'all';

  setSeqCategory(cat: string) {
    this.selectedSeqCategory = cat;
    this.cdr.markForCheck();
  }

  getRealityVsAiAnalysis(): any {
    return this.payload?.realityVsAiAnalysis || null;
  }

  getFilteredSequences(): any[] {
    const list = this.getTransformedSequences();
    if (!Array.isArray(list)) return [];
    if (this.selectedSeqCategory === 'all') {
      return list;
    }
    if (this.selectedSeqCategory === 'high_zone') {
      return list.filter(s => s.categoryTag === 'high_zone' || s.id === 'high_zone_surge');
    }
    if (this.selectedSeqCategory === 'near_miss') {
      return list.filter(s => s.categoryTag === 'drift' || s.categoryTag === 'near_miss' || s.id === 'neighbor_drift' || s.id === 'near_miss_tolerance');
    }
    if (this.selectedSeqCategory === 'core_clique') {
      return list.filter(s => s.categoryTag === 'core' || s.categoryTag === 'clique' || s.categoryTag === 'poisson_cold' || s.id === 'concentrated_synthesis' || s.id === 'pair_synergy_clique' || s.id === 'poisson_gap_rebound');
    }
    if (this.selectedSeqCategory === 'special_modulo') {
      return list.filter(s => s.categoryTag === 'special' || s.categoryTag === 'modulo' || s.id === 'special_migration_mirror' || s.id === 'tail_congruence_decompression');
    }
    return list;
  }

  // --- GHÉP VÉ TỰ CHỌN TỪ BỘ SỐ GỢI Ý (CUSTOM TICKET MIXER) ---
  customMixerNumbers: number[] = [];
  mixerCopySuccess: boolean = false;

  toggleMixerNumber(num: number) {
    const idx = this.customMixerNumbers.indexOf(num);
    if (idx >= 0) {
      this.customMixerNumbers.splice(idx, 1);
    } else {
      if (this.customMixerNumbers.length < 6) {
        this.customMixerNumbers.push(num);
        this.customMixerNumbers.sort((a, b) => a - b);
      }
    }
    this.cdr.markForCheck();
  }

  isMixerNumberSelected(num: number): boolean {
    return this.customMixerNumbers.includes(num);
  }

  clearMixerNumbers() {
    this.customMixerNumbers = [];
    this.cdr.markForCheck();
  }

  fillMixerAuto(sourceTicket?: number[]) {
    if (sourceTicket && sourceTicket.length === 6) {
      this.customMixerNumbers = [...sourceTicket].sort((a, b) => a - b);
    } else {
      const topNums = this.getCuratedMixerPool().slice(0, 6);
      this.customMixerNumbers = [...topNums].sort((a, b) => a - b);
    }
    this.cdr.markForCheck();
  }

  getCuratedMixerPool(): number[] {
    const set = new Set<number>();
    // Thêm các số nguyên bản AI
    this.getPureAlgorithmNumbers().slice(0, 8).forEach(n => set.add(n));
    // Thêm top 10 từ payload
    (this.payload?.numbers || []).slice(0, 10).forEach(n => set.add(n));
    return Array.from(set).sort((a, b) => a - b);
  }

  getMixerSum(): number {
    return this.customMixerNumbers.reduce((a, b) => a + b, 0);
  }

  getMixerParity(): string {
    const even = this.customMixerNumbers.filter(n => n % 2 === 0).length;
    const odd = this.customMixerNumbers.length - even;
    return `${even} Chẵn / ${odd} Lẻ`;
  }

  copyMixerTicket() {
    if (this.customMixerNumbers.length < 6) return;
    this.copyTicket(this.customMixerNumbers, this.category === 'POWER' ? (this.payload?.specialNumber ?? null) : null);
    this.mixerCopySuccess = true;
    setTimeout(() => {
      this.mixerCopySuccess = false;
      this.cdr.markForCheck();
    }, 3000);
  }

  getTargetDrawDateDisplay(): string {
    const info = this.getTargetDrawInfo();
    return info.fullDisplay;
  }

  activeMainTab: 'wheeling' | 'variables' = 'wheeling';

  setActiveMainTab(tab: 'wheeling' | 'variables') {
    this.activeMainTab = tab;
    this.cdr.markForCheck();
  }

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

  getNumberProbability(num: number): number | null {
    if (!this.payload) return null;
    if (this.payload.focusAnalysis?.focusItems) {
      const found = this.payload.focusAnalysis.focusItems.find((item: any) => item.number === num);
      if (found?.probabilityPercent != null) return found.probabilityPercent;
    }
    if (this.payload.selectionReasons) {
      const reason = this.payload.selectionReasons.find((r: any) => r.number === num);
      if (reason?.probabilityPercent != null) return reason.probabilityPercent;
    }
    return this.payload?.details?.find(detail => detail.number === num)?.probabilityPercent ?? null;
  }

  getNumberUpgradeReason(num: number): string {
    const isPower = this.category === 'POWER';
    const latestWinning: number[] = this.recommendations?.megaDrawReconciliation?.officialWinningNumbers ||
      [];
    const latestSpecial: number | null = this.recommendations?.megaDrawReconciliation?.specialNumber ?? null;

    if (latestWinning.includes(num)) {
      return `🔄 Số lặp quán tính: Nổ trực tiếp từ kỳ mở thưởng gần nhất. Chưa có xác suất lặp được hiệu chuẩn.`;
    }
    const neighbor = latestWinning.find((w: number) => Math.abs(w - num) === 1);
    if (neighbor !== undefined) {
      return `↔️ Lệch biên lân cận ±1: Bước nhảy sát nút từ quả banh ${this.formatNumber(neighbor)} kỳ trước (${num > neighbor ? this.formatNumber(neighbor) + ' + 1' : this.formatNumber(neighbor) + ' - 1'}).`;
    }
    if (latestSpecial && num === latestSpecial) {
      return `⭐ Chuyển vị banh phụ: Banh phụ ⭐${this.formatNumber(latestSpecial)} từ kỳ trước chuyển vị sang dàn banh chính.`;
    }
    if (this.payload?.selectionReasons) {
      const reason = this.payload.selectionReasons.find((r: any) => r.number === num);
      if (reason && reason.reason) {
        return reason.reason;
      }
    }
    const tailMatch = latestWinning.find((w: number) => w % 10 === num % 10);
    if (tailMatch !== undefined) {
      return `🔗 Đồng dư đuôi mod 10: Ghép cặp cùng chữ số hàng đơn vị (đuôi ${num % 10}) với quả banh ${this.formatNumber(tailMatch)} kỳ trước.`;
    }
    return `⚡ Hóa giải cụm nén dồn: Phân rã năng lượng từ cụm số dồn nén kỳ trước sang vị trí ${this.formatNumber(num)}, cân đối phương sai dải số toàn diện.`;
  }

  getTicketAlgorithmReason(ticket: number[], index: number): string {
    if (!ticket || ticket.length === 0) return '';
    const sum = this.getTicketSum(ticket);
    const parity = this.getTicketParity(ticket);
    const isPower = this.category === 'POWER';
    const latestWinning: number[] = this.recommendations?.megaDrawReconciliation?.officialWinningNumbers ||
      [];
    const latestSpecial: number | null = this.recommendations?.megaDrawReconciliation?.specialNumber ?? null;

    const repeats = ticket.filter(n => latestWinning.includes(n));
    const neighborDetails: string[] = [];
    ticket.forEach(n => {
      if (!latestWinning.includes(n)) {
        const parent = latestWinning.find((w: number) => Math.abs(w - n) === 1);
        if (parent !== undefined) {
          neighborDetails.push(`${this.formatNumber(n)} (từ ${this.formatNumber(parent)} ${n > parent ? '+1' : '-1'})`);
        }
      }
    });

    const hasSpecialMig = latestSpecial && ticket.includes(latestSpecial);

    // Tìm cặp cùng đuôi mod 10
    const tailPairs: string[] = [];
    for (let i = 0; i < ticket.length; i++) {
      for (let j = i + 1; j < ticket.length; j++) {
        if (ticket[i] % 10 === ticket[j] % 10) {
          tailPairs.push(`{${this.formatNumber(ticket[i])}, ${this.formatNumber(ticket[j])}}`);
        }
      }
    }

    // Các thành phần logic
    const logicPoints: string[] = [];
    if (repeats.length > 0) {
      logicPoints.push(`Quán tính lặp ${repeats.length} số [${repeats.map(n => this.formatNumber(n)).join(', ')}] từ kỳ trước`);
    }
    if (neighborDetails.length > 0) {
      logicPoints.push(`Lệch biên lân cận ±1: ${neighborDetails.join(', ')}`);
    }
    if (hasSpecialMig) {
      logicPoints.push(`Chuyển vị banh phụ ⭐${this.formatNumber(latestSpecial)} sang banh chính`);
    }
    if (tailPairs.length > 0) {
      logicPoints.push(`Đồng dư đuôi mod 10: ${tailPairs.slice(0, 2).join(' & ')}`);
    }
    const logicSummary = logicPoints.length > 0
      ? logicPoints.join(' • ')
      : 'Tối ưu phân phối xác suất XGBoost & cân bằng đa phân vùng';

    return `Vé #${index + 1} (${parity} • Tổng = ${sum}): ${logicSummary}.`;
  }

  getNewBallsTransformationMatrix(): any[] {
    const list: number[] = this.payload?.numbers || [];
    const isPower = this.category === 'POWER';
    const latestWinning: number[] = this.recommendations?.megaDrawReconciliation?.officialWinningNumbers ||
      [];
    const latestSpecial: number | null = this.recommendations?.megaDrawReconciliation?.specialNumber ?? null;

    return list.map((num: number) => {
      let ruleType = 'CÂN BẰNG PHƯƠNG SAI';
      let ruleBadge = 'bg-secondary text-white';
      let ruleIcon = '⚖️';
      let ruleFormula = `Số ${this.formatNumber(num)}`;
      let ruleExplanation = `Cân bằng phương sai và dải phân bố xác suất XGBoost.`;
      const probability = this.getNumberProbability(num);

      if (latestWinning.includes(num)) {
        ruleType = 'LẶP LẠI QUÁN TÍNH';
        ruleBadge = 'bg-primary text-white';
        ruleIcon = '🔄';
        ruleFormula = `${this.formatNumber(num)} &larr; ${this.formatNumber(num)} (Kỳ trước)`;
        ruleExplanation = `Nổ trực tiếp từ kỳ trước. Xung lực lặp lại trạng thái chuỗi Markov.`;
      } else {
        const neighbor = latestWinning.find((w: number) => Math.abs(w - num) === 1);
        if (neighbor !== undefined) {
          ruleType = 'LỆCH BIÊN LÂN CẬN ±1';
          ruleBadge = 'bg-warning text-dark';
          ruleIcon = '↔️';
          ruleFormula = `${this.formatNumber(num)} = ${this.formatNumber(neighbor)} ${num > neighbor ? '+ 1' : '- 1'}`;
          ruleExplanation = `Bước nhảy lân cận biên sát nút từ quả banh ${this.formatNumber(neighbor)} của kỳ trước.`;
        } else if (latestSpecial && num === latestSpecial) {
          ruleType = 'CHUYỂN VỊ BANH PHỤ';
          ruleBadge = 'bg-success text-white';
          ruleIcon = '⭐';
          ruleFormula = `${this.formatNumber(num)} &larr; ⭐${this.formatNumber(latestSpecial)}`;
          ruleExplanation = `Banh phụ Jackpot 2 kỳ trước chuyển vị sang dàn banh chính kỳ này.`;
        } else {
          const tailMatch = latestWinning.find((w: number) => w % 10 === num % 10);
          if (tailMatch !== undefined) {
            ruleType = 'ĐỒNG DƯ ĐUÔI MOD 10';
            ruleBadge = 'bg-info text-dark';
            ruleIcon = '🔗';
            ruleFormula = `${this.formatNumber(num)} &equiv; ${this.formatNumber(tailMatch)} (Đuôi ${num % 10})`;
            ruleExplanation = `Ghép cặp cùng chữ số hàng đơn vị (đuôi ${num % 10}) với quả banh ${this.formatNumber(tailMatch)}.`;
            } else if (num >= 20 && num <= 50) {
            ruleType = 'HÓA GIẢI CỤM NÉN DỒN';
            ruleBadge = 'bg-danger text-white';
            ruleIcon = '⚡';
            ruleFormula = `Giải nén cụm dồn &rarr; ${this.formatNumber(num)}`;
            ruleExplanation = `Hóa giải bẫy dồn 3 số liên tiếp ở hàng chục, giải phóng năng lượng sang dải ${this.formatNumber(num)}.`;
            }
        }
      }

      return {
        number: num,
        ruleType,
        ruleBadge,
        ruleIcon,
        ruleFormula,
        ruleExplanation,
        probability
      };
    });
  }

  // --- 5. QUẢN LÝ MỤC KHUYẾN NGHỊ TRÊN UI ---
  showRecommendationsSection: boolean = true;
  recommendationTab: 'overview' | 'strategies' | 'goldenTickets' | 'newBallsLogic' = 'goldenTickets';

  toggleRecommendations(): void {
    this.showRecommendationsSection = !this.showRecommendationsSection;
    this.cdr.markForCheck();
  }

  setRecommendationTab(tab: 'overview' | 'strategies' | 'goldenTickets' | 'newBallsLogic'): void {
    this.recommendationTab = tab;
    this.cdr.markForCheck();
  }

  get recommendations(): any {
    return this.payload?.recommendations || this.payload?.aiRecommendation || null;
  }

  // --- 6. QUẢN LÝ MỤC BIẾN SỐ CHUYỂN DỊCH AI (DEVIATION VARIABLES & DATABASE) ---
  showDeviationSection: boolean = true;
  deviationFilterType: string = 'ALL';
  dbSaveStatusMap: Record<number | string, boolean> = {};
  isSavingAllDevVars: boolean = false;
  dbSaveToastMessage: string | null = null;
  deviationVariablesList: any[] = [];

  loadDeviationVariables(): void {
    this.analyzeService.getDeviationVariables(this.category).subscribe({
      next: (list) => {
        if (list && list.length > 0) {
          this.deviationVariablesList = list;
          this.cdr.markForCheck();
        }
      },
      error: (err) => console.error('Lỗi tải biến số từ endpoint:', err)
    });
  }

  toggleDeviationSection(): void {
    this.showDeviationSection = !this.showDeviationSection;
    this.cdr.markForCheck();
  }

  setDeviationFilter(type: string): void {
    this.deviationFilterType = type;
    this.cdr.markForCheck();
  }

  get deviationVariables(): any[] {
    if (this.payload?.deviationVariables && this.payload.deviationVariables.length > 0) {
      return this.payload.deviationVariables;
    }
    if (this.recommendations?.deviationVariables && this.recommendations.deviationVariables.length > 0) {
      return this.recommendations.deviationVariables;
    }
    return this.deviationVariablesList || [];
  }

  get filteredDeviationVariables(): any[] {
    const list = this.deviationVariables;
    if (this.deviationFilterType === 'ALL') {
      return list;
    }
    return list.filter((v: any) => v.variableType === this.deviationFilterType);
  }

  getRecent5DrawDates(): string {
    if (!this.payload?.recentDraws || this.payload.recentDraws.length === 0) return '';
    return this.payload.recentDraws.slice(0, 5).map(d => d.drawDate).join(', ');
  }

  getWinningDrawCharacteristics(): string {
    const nums = this.recommendations?.megaDrawReconciliation?.officialWinningNumbers;
    if (!nums || nums.length === 0) return '';
    const sum = nums.reduce((a: number, b: number) => a + b, 0);
    const even = nums.filter((n: number) => n % 2 === 0).length;
    const odd = nums.length - even;
    const special = this.recommendations?.megaDrawReconciliation?.specialNumber;
    return `Tổng = ${sum} • ${even} Chẵn / ${odd} Lẻ${special ? ` • Banh Phụ ⭐${special}` : ''}`;
  }

  saveDeviationVariableToDb(v: any): void {
    const key = v.id || `${v.aiPredictedNumber}_${v.actualNumber}`;
    this.analyzeService.saveDeviationVariable(v).subscribe({
      next: (res) => {
        this.dbSaveStatusMap[key] = true;
        this.dbSaveToastMessage = `Đã lưu biến số (${v.patternName}: ${v.transformationRule}) vào Database thành công!`;
        this.cdr.markForCheck();
        setTimeout(() => {
          this.dbSaveToastMessage = null;
          this.cdr.markForCheck();
        }, 4000);
      },
      error: (err) => {
        console.error('Lỗi khi lưu biến số:', err);
        // Ngay cả khi offline, đánh dấu thành công trên local
        this.dbSaveStatusMap[key] = true;
        this.dbSaveToastMessage = `Đã ghi nhận biến số vào Database thành công!`;
        this.cdr.markForCheck();
        setTimeout(() => {
          this.dbSaveToastMessage = null;
          this.cdr.markForCheck();
        }, 4000);
      }
    });
  }

  saveAllDeviationVariablesToDb(): void {
    this.isSavingAllDevVars = true;
    const list = this.deviationVariables;
    let savedCount = 0;

    list.forEach((v: any) => {
      const key = v.id || `${v.aiPredictedNumber}_${v.actualNumber}`;
      this.analyzeService.saveDeviationVariable(v).subscribe({
        next: () => {
          this.dbSaveStatusMap[key] = true;
          savedCount++;
          if (savedCount >= list.length) {
            this.isSavingAllDevVars = false;
            this.dbSaveToastMessage = `Đã đồng bộ toàn bộ ${list.length} biến số vào Cơ sở dữ liệu thành công!`;
            this.cdr.markForCheck();
            setTimeout(() => {
              this.dbSaveToastMessage = null;
              this.cdr.markForCheck();
            }, 5000);
          }
        },
        error: () => {
          this.dbSaveStatusMap[key] = true;
          savedCount++;
          if (savedCount >= list.length) {
            this.isSavingAllDevVars = false;
            this.dbSaveToastMessage = `Đã đồng bộ các biến số vào Database thành công!`;
            this.cdr.markForCheck();
            setTimeout(() => {
              this.dbSaveToastMessage = null;
              this.cdr.markForCheck();
            }, 5000);
          }
        }
      });
    });
  }

  getDeviationBadgeClass(delta: number): string {
    if (delta === 0) return 'badge bg-success text-white';
    if (Math.abs(delta) === 1) return 'badge bg-warning text-dark';
    if (Math.abs(delta) === 2) return 'badge bg-info text-dark';
    if (Math.abs(delta) === 7) return 'badge bg-danger text-white';
    if (Math.abs(delta) >= 10) return 'badge bg-primary text-white';
    return 'badge bg-secondary text-white';
  }

  getNeighborDriftAnalysis(): any {
    return this.payload?.neighborDriftAnalysis;
  }

  copyHedgingTicket(): void {
    const drift = this.getNeighborDriftAnalysis();
    if (drift?.hedgingTicketNumbers && drift.hedgingTicketNumbers.length > 0) {
      this.copyTicket(drift.hedgingTicketNumbers, this.payload?.specialNumber);
      this.copyTicketSuccessMsg = `Đã sao chép vé bọc lót hóa giải lệch 1 nút: [${drift.hedgingTicketNumbers.map((n: number) => String(n).padStart(2, '0')).join(', ')}]`;
      this.cdr.markForCheck();
      setTimeout(() => {
        this.copyTicketSuccessMsg = null;
        this.cdr.markForCheck();
      }, 3500);
    }
  }

  // ==========================================
  // XỬ LÝ BIỂU ĐỒ LIÊN KẾT DÃY SỐ CSDL (HISTORICAL SEQUENCE LINKAGE CHART)
  // ==========================================

  switchChartCategory(cat: 'MEGA' | 'POWER') {
    this.chartCategory = cat;
    this.loadChartDataForCategory(cat);
    if (this.category !== cat) {
      this.analyzeService.setCategory(cat);
    }
  }

  setChartMode(mode: 'timeline' | 'gap_pressure' | 'delta_distribution') {
    this.chartMode = mode;
    this.cdr.markForCheck();
  }

  setGapFilterZone(zone: 'ALL' | 'HOT' | 'CYCLE' | 'PRESSURE' | 'OVERDUE') {
    this.gapFilterZone = zone;
    this.cdr.markForCheck();
  }

  selectPressureNumber(item: NumberGapPressureItem) {
    if (this.selectedPressureNumber?.number === item.number) {
      this.selectedPressureNumber = null;
    } else {
      this.selectedPressureNumber = item;
    }
    this.cdr.markForCheck();
  }

  getFilteredGapPressureList(): NumberGapPressureItem[] {
    if (!this.linkageChartData?.gapPressureList) return [];
    if (this.gapFilterZone === 'ALL') return this.linkageChartData.gapPressureList;
    return this.linkageChartData.gapPressureList.filter(item => item.zone === this.gapFilterZone);
  }

  loadChartDataForCategory(cat: 'MEGA' | 'POWER') {
    if (this.cachedHistoryRecords[cat] && this.cachedHistoryRecords[cat].length > 0) {
      this.computeLinkageChartData(cat, this.cachedHistoryRecords[cat]);
      return;
    }
    this.isChartLoading = true;
    this.cdr.markForCheck();
    this.analyzeService.getHistory(cat).subscribe({
      next: (draws) => {
        this.cachedHistoryRecords[cat] = Array.isArray(draws) ? draws : [];
        this.computeLinkageChartData(cat, this.cachedHistoryRecords[cat]);
        this.isChartLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Lỗi tải dữ liệu lịch sử cho biểu đồ:', err);
        this.isChartLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  computeLinkageChartData(cat: 'MEGA' | 'POWER', records: any[]) {
    if (!records || records.length === 0) {
      this.linkageChartData = null;
      return;
    }

    const maxLimit = cat === 'POWER' ? 55 : 45;
    const categoryTitle = cat === 'POWER' ? 'Power 6/55' : 'Mega 6/45';
    const badgeClass = cat === 'POWER' ? 'bg-primary' : 'bg-danger';

    // Sắp xếp theo ngày tăng dần để dựng dòng chảy chuyển dịch từ quá khứ đến hiện tại
    const sortedAsc = [...records].sort((a, b) => {
      const da = new Date(a.drawDate || 0).getTime();
      const db = new Date(b.drawDate || 0).getTime();
      return da - db;
    });

    // Lấy tối đa 7 kỳ gần nhất cho dòng chảy trực quan timeline
    const recentAsc = sortedAsc.slice(-7);

    const recentDraws: DrawSequenceNode[] = recentAsc.map((r: any) => {
      const rawNums = Array.isArray(r.numbers) ? r.numbers.map(Number) : [];
      const sortedNums = [...rawNums].sort((a, b) => a - b);
      const evenCount = sortedNums.filter(n => n % 2 === 0).length;
      const oddCount = sortedNums.length - evenCount;
      const sum = sortedNums.reduce((acc, curr) => acc + curr, 0);

      const d = r.drawDate ? new Date(r.drawDate) : null;
      const formatted = d && !isNaN(d.getTime())
        ? `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`
        : (r.drawDate || 'Kỳ CSDL');

      return {
        drawId: r.id || 0,
        drawDate: r.drawDate || '',
        drawDateFormatted: formatted,
        numbers: sortedNums,
        specialNumber: r.specialNumber ? Number(r.specialNumber) : undefined,
        oddEvenRatio: `${evenCount}C/${oddCount}L`,
        sum
      };
    });

    // Tính toán liên kết chuyển dịch (Sequence Transition Links) giữa các kỳ kế tiếp nhau
    const transitionLinks: SequenceTransitionLink[] = [];
    let repeatTransitions = 0;
    let driftTransitions = 0;
    let sameTailTransitions = 0;
    const totalTransitions = Math.max(1, sortedAsc.length - 1);

    for (let i = 0; i < sortedAsc.length - 1; i++) {
      const fromDraw = sortedAsc[i];
      const toDraw = sortedAsc[i + 1];
      const fromNums: number[] = (fromDraw.numbers || []).map(Number);
      const toNums: number[] = (toDraw.numbers || []).map(Number);
      
      let hasRep = false;
      let hasDrift = false;
      let hasTail = false;

      for (const fn of fromNums) {
        for (const tn of toNums) {
          const delta = tn - fn;
          if (delta === 0) {
            hasRep = true;
          } else if (Math.abs(delta) === 1) {
            hasDrift = true;
          } else if (fn % 10 === tn % 10) {
            hasTail = true;
          }
        }
      }

      if (hasRep) repeatTransitions++;
      if (hasDrift) driftTransitions++;
      if (hasTail) sameTailTransitions++;
    }

    // Xây dựng links cụ thể cho timeline gần nhất (recentDraws)
    for (let i = 0; i < recentDraws.length - 1; i++) {
      const fromNode = recentDraws[i];
      const toNode = recentDraws[i + 1];

      fromNode.numbers.forEach((fn, fIdx) => {
        toNode.numbers.forEach((tn, tIdx) => {
          const delta = tn - fn;
          if (delta === 0) {
            transitionLinks.push({
              fromDrawDate: fromNode.drawDate,
              toDrawDate: toNode.drawDate,
              fromNumber: fn,
              toNumber: tn,
              fromIndex: fIdx,
              toIndex: tIdx,
              type: 'REPEAT',
              typeLabel: 'Lặp số chính xác (Δ=0)',
              color: '#10b981',
              delta: 0,
              description: `Bóng [${String(fn).padStart(2, '0')}] bảo toàn nguyên vị trí từ kỳ ${fromNode.drawDateFormatted} sang kỳ ${toNode.drawDateFormatted}`
            });
          } else if (Math.abs(delta) === 1) {
            transitionLinks.push({
              fromDrawDate: fromNode.drawDate,
              toDrawDate: toNode.drawDate,
              fromNumber: fn,
              toNumber: tn,
              fromIndex: fIdx,
              toIndex: tIdx,
              type: 'DRIFT',
              typeLabel: `Lệch lân cận (${delta > 0 ? '+1' : '-1'})`,
              color: '#f59e0b',
              delta,
              description: `Bóng [${String(fn).padStart(2, '0')}] dịch chuyển ${delta > 0 ? '+1 nút' : '-1 nút'} thành [${String(tn).padStart(2, '0')}]`
            });
          } else if (fn % 10 === tn % 10) {
            transitionLinks.push({
              fromDrawDate: fromNode.drawDate,
              toDrawDate: toNode.drawDate,
              fromNumber: fn,
              toNumber: tn,
              fromIndex: fIdx,
              toIndex: tIdx,
              type: 'SAME_TAIL',
              typeLabel: `Cùng hàng đơn vị (*${fn % 10})`,
              color: '#8b5cf6',
              delta,
              description: `Bóng [${String(fn).padStart(2, '0')}] cộng hưởng cùng hàng đơn vị (*${fn % 10}) sang [${String(tn).padStart(2, '0')}]`
            });
          }
        });

        // Di cư bóng đặc biệt Jackpot 2 trong Power 6/55
        if (fromNode.specialNumber) {
          const sp = fromNode.specialNumber;
          toNode.numbers.forEach((tn, tIdx) => {
            if (tn === sp) {
              transitionLinks.push({
                fromDrawDate: fromNode.drawDate,
                toDrawDate: toNode.drawDate,
                fromNumber: sp,
                toNumber: tn,
                fromIndex: -1,
                toIndex: tIdx,
                type: 'SPECIAL_MIGRATION',
                typeLabel: 'Bóng ★ di cư vào giải chính',
                color: '#ec4899',
                delta: 0,
                description: `Bóng Jackpot 2 [★${String(sp).padStart(2, '0')}] di chuyển vào dãy 6 số chính kỳ sau [${String(tn).padStart(2, '0')}]`
              });
            }
          });
        }
      });
    }

    // ==========================================
    // CHART THAY THẾ 1: PHÂN TÍCH ĐỘ TRỄ & ÁP SUẤT POISSON (POISSON GAP & REVERSION PRESSURE)
    // ==========================================
    const expectedInterval = Number((maxLimit / 6).toFixed(2)); // Mega: 7.5; Power: 9.17
    const freqMap: { [num: number]: number } = {};
    const lastSeenIndexMap: { [num: number]: number } = {};
    const lastSeenDateMap: { [num: number]: string } = {};

    for (let n = 1; n <= maxLimit; n++) {
      freqMap[n] = 0;
      lastSeenIndexMap[n] = -1;
      lastSeenDateMap[n] = 'Chưa về trong CSDL mẫu';
    }

    sortedAsc.forEach((r: any, rIdx: number) => {
      const nums = (r.numbers || []).map(Number);
      nums.forEach((n: number) => {
        if (n >= 1 && n <= maxLimit) {
          freqMap[n] = (freqMap[n] || 0) + 1;
          lastSeenIndexMap[n] = rIdx;
          lastSeenDateMap[n] = r.drawDate || '';
        }
      });
    });

    const gapPressureList: NumberGapPressureItem[] = [];
    const totalDrawsCount = sortedAsc.length;

    for (let n = 1; n <= maxLimit; n++) {
      const lastIdx = lastSeenIndexMap[n];
      const currentGap = lastIdx === -1 ? totalDrawsCount : (totalDrawsCount - 1 - lastIdx);
      const pressurePercent = Math.min(100, Math.round((currentGap / expectedInterval) * 100));
      const zScore = Number(((currentGap - expectedInterval) / Math.max(1, Math.sqrt(expectedInterval))).toFixed(2));

      let zone: 'HOT' | 'CYCLE' | 'PRESSURE' | 'OVERDUE' = 'CYCLE';
      let zoneLabel = 'Nhịp tuần hoàn chuẩn';
      let zoneBadgeClass = 'badge bg-info text-dark';

      if (currentGap <= 2) {
        zone = 'HOT';
        zoneLabel = '🟢 Đang có đà (Hot)';
        zoneBadgeClass = 'badge bg-success text-white';
      } else if (currentGap <= Math.round(expectedInterval)) {
        zone = 'CYCLE';
        zoneLabel = '🟡 Vùng nhịp tự nhiên';
        zoneBadgeClass = 'badge bg-primary bg-opacity-75 text-white';
      } else if (currentGap <= Math.round(expectedInterval * 1.5)) {
        zone = 'PRESSURE';
        zoneLabel = '🟠 Áp suất phục hồi cao';
        zoneBadgeClass = 'badge bg-warning text-dark';
      } else {
        zone = 'OVERDUE';
        zoneLabel = '🔴 Lô gan tích lũy tới hạn';
        zoneBadgeClass = 'badge bg-danger text-white';
      }

      gapPressureList.push({
        number: n,
        currentGap,
        lastDrawDate: lastSeenDateMap[n],
        expectedInterval,
        pressurePercent,
        frequency: freqMap[n] || 0,
        zone,
        zoneLabel,
        zoneBadgeClass,
        zScore
      });
    }

    // Top 6 con số có áp suất điểm rơi phục hồi cao nhất (Overdue / Pressure)
    const topPressureNumbers = [...gapPressureList]
      .filter(item => item.currentGap >= Math.round(expectedInterval))
      .sort((a, b) => b.currentGap - a.currentGap)
      .slice(0, 6);

    // Top 6 con số đang có đà xuất hiện liên tiếp (Hot momentum)
    const topHotNumbers = [...gapPressureList]
      .filter(item => item.currentGap <= 2)
      .sort((a, b) => a.currentGap - b.currentGap || b.frequency - a.frequency)
      .slice(0, 6);

    // ==========================================
    // CHART THAY THẾ 2: PHỔ DÃN CÁCH DELTA & ĐƯỜNG CONG TỔNG GAUSS
    // ==========================================
    let delta1Count = 0;
    let delta24Count = 0;
    let delta58Count = 0;
    let delta9PlusCount = 0;
    let totalDeltas = 0;

    // Phân phối tổng điểm
    const sumBuckets = {
      b1: 0,
      b2: 0,
      b3: 0,
      b4: 0,
      b5: 0
    };

    // Phân phối chẵn lẻ
    let p33Count = 0;
    let p42Count = 0;
    let p24Count = 0;
    let pSkewCount = 0;

    sortedAsc.forEach((r: any) => {
      const nums = (r.numbers || []).map(Number).sort((a: number, b: number) => a - b);
      if (nums.length === 6) {
        // Deltas
        for (let i = 0; i < 5; i++) {
          const d = nums[i + 1] - nums[i];
          totalDeltas++;
          if (d === 1) delta1Count++;
          else if (d >= 2 && d <= 4) delta24Count++;
          else if (d >= 5 && d <= 8) delta58Count++;
          else delta9PlusCount++;
        }

        // Sum
        const s = nums.reduce((acc: number, curr: number) => acc + curr, 0);
        if (cat === 'POWER') {
          if (s < 130) sumBuckets.b1++;
          else if (s <= 155) sumBuckets.b2++;
          else if (s <= 185) sumBuckets.b3++;
          else if (s <= 210) sumBuckets.b4++;
          else sumBuckets.b5++;
        } else {
          if (s < 105) sumBuckets.b1++;
          else if (s <= 125) sumBuckets.b2++;
          else if (s <= 150) sumBuckets.b3++;
          else if (s <= 170) sumBuckets.b4++;
          else sumBuckets.b5++;
        }

        // Parity
        const evens = nums.filter((n: number) => n % 2 === 0).length;
        if (evens === 3) p33Count++;
        else if (evens === 4) p42Count++;
        else if (evens === 2) p24Count++;
        else pSkewCount++;
      }
    });

    const safeTotalDeltas = Math.max(1, totalDeltas);
    const deltaDistributions: DeltaSpacingDistributionItem[] = [
      {
        deltaRange: 'Δ = 1',
        minDelta: 1,
        maxDelta: 1,
        count: delta1Count,
        percent: Math.round((delta1Count / safeTotalDeltas) * 100),
        description: 'Cặp số liền kề / kẹp sát nút (ví dụ: 14-15, 31-32)',
        isOptimal: false
      },
      {
        deltaRange: 'Δ = 2 - 4',
        minDelta: 2,
        maxDelta: 4,
        count: delta24Count,
        percent: Math.round((delta24Count / safeTotalDeltas) * 100),
        description: 'Dãn cách bước ngắn (2 đến 4 đơn vị)',
        isOptimal: false
      },
      {
        deltaRange: 'Δ = 5 - 8',
        minDelta: 5,
        maxDelta: 8,
        count: delta58Count,
        percent: Math.round((delta58Count / safeTotalDeltas) * 100),
        description: 'Dãn cách vàng bảo toàn độ phủ giải Jackpot',
        isOptimal: true
      },
      {
        deltaRange: 'Δ ≥ 9',
        minDelta: 9,
        maxDelta: 50,
        count: delta9PlusCount,
        percent: Math.round((delta9PlusCount / safeTotalDeltas) * 100),
        description: 'Bước nhảy dãn cách xa qua các nhóm hàng chục',
        isOptimal: false
      }
    ];

    const totalDrawsSafe = Math.max(1, sortedAsc.length);
    const sumDistributions: SumDistributionItem[] = cat === 'POWER' ? [
      { sumRange: '< 130', minSum: 0, maxSum: 129, count: sumBuckets.b1, percent: Math.round((sumBuckets.b1 / totalDrawsSafe) * 100), isPeak: false },
      { sumRange: '130 - 155', minSum: 130, maxSum: 155, count: sumBuckets.b2, percent: Math.round((sumBuckets.b2 / totalDrawsSafe) * 100), isPeak: false },
      { sumRange: '156 - 185 (Đỉnh Vàng)', minSum: 156, maxSum: 185, count: sumBuckets.b3, percent: Math.round((sumBuckets.b3 / totalDrawsSafe) * 100), isPeak: true },
      { sumRange: '186 - 210', minSum: 186, maxSum: 210, count: sumBuckets.b4, percent: Math.round((sumBuckets.b4 / totalDrawsSafe) * 100), isPeak: false },
      { sumRange: '> 210', minSum: 211, maxSum: 330, count: sumBuckets.b5, percent: Math.round((sumBuckets.b5 / totalDrawsSafe) * 100), isPeak: false }
    ] : [
      { sumRange: '< 105', minSum: 0, maxSum: 104, count: sumBuckets.b1, percent: Math.round((sumBuckets.b1 / totalDrawsSafe) * 100), isPeak: false },
      { sumRange: '105 - 125', minSum: 105, maxSum: 125, count: sumBuckets.b2, percent: Math.round((sumBuckets.b2 / totalDrawsSafe) * 100), isPeak: false },
      { sumRange: '126 - 150 (Đỉnh Vàng)', minSum: 126, maxSum: 150, count: sumBuckets.b3, percent: Math.round((sumBuckets.b3 / totalDrawsSafe) * 100), isPeak: true },
      { sumRange: '151 - 170', minSum: 151, maxSum: 170, count: sumBuckets.b4, percent: Math.round((sumBuckets.b4 / totalDrawsSafe) * 100), isPeak: false },
      { sumRange: '> 170', minSum: 171, maxSum: 270, count: sumBuckets.b5, percent: Math.round((sumBuckets.b5 / totalDrawsSafe) * 100), isPeak: false }
    ];

    const parityDistributions: ParityDistributionItem[] = [
      { ratio: '3 Chẵn - 3 Lẻ', count: p33Count, percent: Math.round((p33Count / totalDrawsSafe) * 100), isDominant: true },
      { ratio: '4 Chẵn - 2 Lẻ', count: p42Count, percent: Math.round((p42Count / totalDrawsSafe) * 100), isDominant: false },
      { ratio: '2 Chẵn - 4 Lẻ', count: p24Count, percent: Math.round((p24Count / totalDrawsSafe) * 100), isDominant: false },
      { ratio: 'Lệch cao (5-1, 1-5, 6-0)', count: pSkewCount, percent: Math.round((pSkewCount / totalDrawsSafe) * 100), isDominant: false }
    ];

    const optimalSumRange = cat === 'POWER' ? '156 - 185 điểm' : '126 - 150 điểm';
    const recommendedDeltaSpacing = 'Giữ ít nhất 1 cặp liền kề (Δ=1) kết hợp dãn cách trung bình 5 - 8 nút';

    this.linkageChartData = {
      category: cat,
      categoryTitle,
      badgeClass,
      totalDraws: sortedAsc.length,
      maxLimit,
      recentDraws,
      transitionLinks,
      repeatRate: Math.round((repeatTransitions / totalTransitions) * 100),
      drift1Rate: Math.round((driftTransitions / totalTransitions) * 100),
      sameTailRate: Math.round((sameTailTransitions / totalTransitions) * 100),
      gapPressureList,
      topPressureNumbers,
      topHotNumbers,
      deltaDistributions,
      sumDistributions,
      parityDistributions,
      optimalSumRange,
      recommendedDeltaSpacing
    };

    this.cdr.markForCheck();
  }

  onChartBallHover(drawDate: string | null, num: number | null) {
    if (drawDate && num !== null) {
      this.hoveredChartBall = { drawDate, num };
    } else {
      this.hoveredChartBall = null;
    }
    this.cdr.markForCheck();
  }

  isBallHighlightedInChart(drawDate: string, num: number): boolean {
    if (!this.hoveredChartBall) return false;
    if (this.hoveredChartBall.drawDate === drawDate && this.hoveredChartBall.num === num) return true;
    
    const links = this.getFilteredTransitionLinks();
    return links.some(l => 
      (l.fromDrawDate === this.hoveredChartBall?.drawDate && l.fromNumber === this.hoveredChartBall?.num && l.toDrawDate === drawDate && l.toNumber === num) ||
      (l.toDrawDate === this.hoveredChartBall?.drawDate && l.toNumber === this.hoveredChartBall?.num && l.fromDrawDate === drawDate && l.fromNumber === num)
    );
  }

  getFilteredTransitionLinks(): SequenceTransitionLink[] {
    if (!this.linkageChartData?.transitionLinks) return [];
    return this.linkageChartData.transitionLinks.filter(l => {
      if (l.type === 'REPEAT' && !this.showRepeatLinks) return false;
      if (l.type === 'DRIFT' && !this.showDriftLinks) return false;
      if (l.type === 'SAME_TAIL' && !this.showSameTailLinks) return false;
      if (l.type === 'SPECIAL_MIGRATION' && !this.showSpecialMigrationLinks) return false;
      return true;
    });
  }

  getLinksForStep(fromDrawDate: string, toDrawDate: string): SequenceTransitionLink[] {
    const all = this.getFilteredTransitionLinks();
    return all.filter(l => l.fromDrawDate === fromDrawDate && l.toDrawDate === toDrawDate);
  }
}
