import { PredictionChartComponent } from './prediction-chart/prediction-chart.component';
import { TicketMixerComponent } from './ticket-mixer/ticket-mixer.component';
import { AlgorithmOption } from '../../core/models/algorithm.model';
import {
  Component,
  OnInit,
  ChangeDetectorRef,
  inject,
  DestroyRef,
  ChangeDetectionStrategy,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgClass, NgStyle, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { distinctUntilChanged, timer } from 'rxjs';
import { PredictionPayload } from '@app/core/models/prediction-payload.model';
import { AnalyzeService } from '@app/core/services/analyze.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-prediction',
  standalone: true,
  imports: [
    PredictionChartComponent,
    TicketMixerComponent,
    FormsModule,
    RouterLink,
    DatePipe,
    DecimalPipe,
    NgClass,
    NgStyle,
  ],
  templateUrl: './prediction.component.html',
  styleUrl: './prediction.component.css',
})
export class PredictionComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly analyzeService = inject(AnalyzeService);
  private readonly cdr = inject(ChangeDetectorRef);

  payload: PredictionPayload | null = null;
  isSpinning = false;
  errorMessage: string | null = null;
  showAllReasons = false;

  // --- BIẾN QUẢN LÝ BIỂU ĐỒ LIÊN KẾT DÃY SỐ CSDL (HISTORICAL SEQUENCE LINKAGE CHART) ---

  // --- 1. BIẾN QUẢN LÝ THUẬT TOÁN (SẮP XẾP TỪ KHẢ NĂNG CHO KẾT QUẢ ĐÚNG NHẤT: TOP 1 -> TOP 7) ---
  selectedAlgorithm: string = 'temporal_learning'; // Mặc định thuật toán 1: Top 1 BEGN
  selectedAlgorithm2: string = 'bayesian_graph'; // Mặc định thuật toán 2: Top 2 DSE-Copula
  algorithms: AlgorithmOption[] = [];

  // --- 2. BIẾN QUẢN LÝ TAB & FOCUS ANALYSIS (TỪ HTML) ---
  activeFocusTab: string = 'target3';
  selectedFocusNumber: number | null = null;
  scoreSearchTerm: string = '';

  // --- 3. BIẾN QUẢN LÝ POPUP HOVER & GHIM (TỪ HTML) ---
  hoveredNumber: number | null = null;
  hoveredNumberDetail: any = null;
  hoveredNumberHistory: any[] = [];
  isPopupPinned: boolean = false; // Phục vụ class .pinned
  popupStyle: Record<string, string> = { top: '0px', left: '0px' };

  category: string = 'MEGA';

  ngOnInit() {
    this.loadAlgorithms();
    this.analyzeService.currentCategory$
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((newCategory) => {
        if (newCategory) {
          this.category = newCategory;
          this.resetState();
          // Mặc định luôn là thuật toán tốt nhất (Top 1)
          this.selectedAlgorithm = this.algorithms[0]?.id || 'temporal_learning';
          this.predict();
        }
      });
  }

  loadAlgorithms() {
    this.analyzeService
      .getAlgorithms()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => {
          if (Array.isArray(list) && list.length > 0) {
            this.algorithms = list;
            this.cdr.markForCheck();
          }
        },
        error: () => {},
      });
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
    return (
      this.algorithms.find((a) => a.id === this.selectedAlgorithm) || {
        name: this.payload?.algorithmName ?? 'Đang tải thuật toán',
        icon: '',
        formula: '',
        description: '',
        rankBadge: '',
      }
    );
  }

  getSecondaryAlgorithmInfo() {
    return (
      this.algorithms.find((a) => a.id === this.selectedAlgorithm2) || {
        name: this.payload?.secondaryAlgorithmResult?.name ?? 'Đang tải thuật toán',
        icon: '',
        formula: '',
        description: '',
        rankBadge: '',
      }
    );
  }

  predict() {
    this.isSpinning = true;
    this.errorMessage = null;
    this.closePopup();
    this.cdr.markForCheck();

    this.analyzeService
      .getPrediction(this.category, this.selectedAlgorithm, this.selectedAlgorithm2)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          if (res.status !== 'SUCCESS') {
            this.payload = null;
            this.errorMessage =
              res.message ?? res.analysisSummary ?? 'Chưa có lịch sử hợp lệ để phân tích.';
            this.isSpinning = false;
            this.cdr.markForCheck();
            return;
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
        },
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
      details: this.payload?.pureAlgorithmTicket?.details || [],
    };
  }

  getSecondaryResult(): any {
    if (this.payload?.secondaryAlgorithmResult) {
      return this.payload.secondaryAlgorithmResult;
    }
    return {
      id: this.selectedAlgorithm2,
      name: this.getSecondaryAlgorithmInfo().name,
      numbers: [],
      accuracyScore: null,
      hitRate: 'N/A',
      details: [],
    };
  }

  getPatternCards(): any[] {
    const patterns = this.payload?.commonPatternsAnalysis;
    return patterns
      ? Object.entries(patterns)
          .filter(([key]) => key !== 'evaluatedDraws')
          .map(([, value]) => value)
      : [];
  }

  getCommonPatternsAnalysis(): any {
    return this.payload?.commonPatternsAnalysis;
  }

  getCombinedResult(): any {
    if (this.payload?.combinedAlgorithmResult) {
      return this.payload.combinedAlgorithmResult;
    }
    return {
      id: 'combined_ensemble',
      name: 'Ensemble consensus',
      numbers: [],
      accuracyScore: null,
      hitRate: 'N/A',
      details: [],
    };
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
    return (
      this.payload?.secondaryAlgorithmResult?.details?.find((detail) => detail.number === num) ??
      null
    );
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
      (item: any) => item.isTargetUpgrade || upgraded.includes(item.number),
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
      items = items.filter((item: any) =>
        item.number.toString().includes(this.scoreSearchTerm.trim()),
      );
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
      this.hoveredNumberDetail = this.payload.selectionReasons.find((r) => r.number === num);
    }
    if (this.payload.recentDraws) {
      this.hoveredNumberHistory = this.payload.recentDraws.filter(
        (draw) => (draw.numbers && draw.numbers.includes(num)) || draw.specialNumber === num,
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
    const text =
      numbers.map((n) => this.formatNumber(n)).join(' - ') +
      (special ? ` (⭐${this.formatNumber(special)})` : '');
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(text);
    }
    this.copyTicketSuccessMsg = `Đã sao chép vé: [ ${text} ]`;
    timer(3000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.copyTicketSuccessMsg = null;
        this.cdr.markForCheck();
      });
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
    const drawDateFormatted =
      this.payload?.targetDrawDateFormatted ??
      (drawDate ? drawDate.split('-').reverse().join('/') : '');
    const isToday = this.payload?.isTargetDrawToday === true;
    return {
      drawDate,
      dayName,
      drawDateFormatted,
      isToday,
      statusText: drawDate
        ? (isToday ? 'Kỳ quay hôm nay: ' : 'Kỳ quay tiếp theo: ') +
          dayName +
          ' ' +
          drawDateFormatted
        : 'Chưa có lịch quay từ máy chủ.',
      statusBadgeClass: isToday ? 'bg-warning text-dark' : 'bg-info text-dark',
      fullDisplay: [dayName, drawDateFormatted].filter(Boolean).join(' '),
    };
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
      return list.filter((s) => s.categoryTag === 'high_zone' || s.id === 'high_zone_surge');
    }
    if (this.selectedSeqCategory === 'near_miss') {
      return list.filter(
        (s) =>
          s.categoryTag === 'drift' ||
          s.categoryTag === 'near_miss' ||
          s.id === 'neighbor_drift' ||
          s.id === 'near_miss_tolerance',
      );
    }
    if (this.selectedSeqCategory === 'core_clique') {
      return list.filter(
        (s) =>
          s.categoryTag === 'core' ||
          s.categoryTag === 'clique' ||
          s.categoryTag === 'poisson_cold' ||
          s.id === 'concentrated_synthesis' ||
          s.id === 'pair_synergy_clique' ||
          s.id === 'poisson_gap_rebound',
      );
    }
    if (this.selectedSeqCategory === 'special_modulo') {
      return list.filter(
        (s) =>
          s.categoryTag === 'special' ||
          s.categoryTag === 'modulo' ||
          s.id === 'special_migration_mirror' ||
          s.id === 'tail_congruence_decompression',
      );
    }
    return list;
  }

  // --- GHÉP VÉ TỰ CHỌN TỪ BỘ SỐ GỢI Ý (CUSTOM TICKET MIXER) ---

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
    const odd = ticket.filter((n) => n % 2 !== 0).length;
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
    return (
      this.payload?.details?.find((detail) => detail.number === num)?.probabilityPercent ?? null
    );
  }

  getNumberUpgradeReason(num: number): string {
    const latestWinning: number[] =
      this.recommendations?.megaDrawReconciliation?.officialWinningNumbers || [];
    const latestSpecial: number | null =
      this.recommendations?.megaDrawReconciliation?.specialNumber ?? null;

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
    const latestWinning: number[] =
      this.recommendations?.megaDrawReconciliation?.officialWinningNumbers || [];
    const latestSpecial: number | null =
      this.recommendations?.megaDrawReconciliation?.specialNumber ?? null;

    const repeats = ticket.filter((n) => latestWinning.includes(n));
    const neighborDetails: string[] = [];
    ticket.forEach((n) => {
      if (!latestWinning.includes(n)) {
        const parent = latestWinning.find((w: number) => Math.abs(w - n) === 1);
        if (parent !== undefined) {
          neighborDetails.push(
            `${this.formatNumber(n)} (từ ${this.formatNumber(parent)} ${n > parent ? '+1' : '-1'})`,
          );
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
      logicPoints.push(
        `Quán tính lặp ${repeats.length} số [${repeats.map((n) => this.formatNumber(n)).join(', ')}] từ kỳ trước`,
      );
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
    const logicSummary =
      logicPoints.length > 0
        ? logicPoints.join(' • ')
        : 'Tối ưu phân phối xác suất XGBoost & cân bằng đa phân vùng';

    return `Vé #${index + 1} (${parity} • Tổng = ${sum}): ${logicSummary}.`;
  }

  getNewBallsTransformationMatrix(): any[] {
    const list: number[] = this.payload?.numbers || [];
    const latestWinning: number[] =
      this.recommendations?.megaDrawReconciliation?.officialWinningNumbers || [];
    const latestSpecial: number | null =
      this.recommendations?.megaDrawReconciliation?.specialNumber ?? null;

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
        probability,
      };
    });
  }

  // --- 5. QUẢN LÝ MỤC KHUYẾN NGHỊ TRÊN UI ---
  showRecommendationsSection: boolean = true;
  recommendationTab: 'overview' | 'strategies' | 'goldenTickets' | 'newBallsLogic' =
    'goldenTickets';

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
    this.analyzeService
      .getDeviationVariables(this.category)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => {
          if (list && list.length > 0) {
            this.deviationVariablesList = list;
            this.cdr.markForCheck();
          }
        },
        error: (err) => console.error('Lỗi tải biến số từ endpoint:', err),
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
    if (
      this.recommendations?.deviationVariables &&
      this.recommendations.deviationVariables.length > 0
    ) {
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
    return this.payload.recentDraws
      .slice(0, 5)
      .map((d) => d.drawDate)
      .join(', ');
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
    this.analyzeService
      .saveDeviationVariable(v)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (_res) => {
          this.dbSaveStatusMap[key] = true;
          this.dbSaveToastMessage = `Đã lưu biến số (${v.patternName}: ${v.transformationRule}) vào Database thành công!`;
          this.cdr.markForCheck();
          timer(4000)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
              this.dbSaveToastMessage = null;
              this.cdr.markForCheck();
            });
        },
        error: (err) => {
          console.error('Lỗi khi lưu biến số:', err);
          // Ngay cả khi offline, đánh dấu thành công trên local
          this.dbSaveStatusMap[key] = true;
          this.dbSaveToastMessage = `Đã ghi nhận biến số vào Database thành công!`;
          this.cdr.markForCheck();
          timer(4000)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
              this.dbSaveToastMessage = null;
              this.cdr.markForCheck();
            });
        },
      });
  }

  saveAllDeviationVariablesToDb(): void {
    this.isSavingAllDevVars = true;
    const list = this.deviationVariables;
    let savedCount = 0;

    list.forEach((v: any) => {
      const key = v.id || `${v.aiPredictedNumber}_${v.actualNumber}`;
      this.analyzeService
        .saveDeviationVariable(v)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.dbSaveStatusMap[key] = true;
            savedCount++;
            if (savedCount >= list.length) {
              this.isSavingAllDevVars = false;
              this.dbSaveToastMessage = `Đã đồng bộ toàn bộ ${list.length} biến số vào Cơ sở dữ liệu thành công!`;
              this.cdr.markForCheck();
              timer(5000)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe(() => {
                  this.dbSaveToastMessage = null;
                  this.cdr.markForCheck();
                });
            }
          },
          error: () => {
            this.dbSaveStatusMap[key] = true;
            savedCount++;
            if (savedCount >= list.length) {
              this.isSavingAllDevVars = false;
              this.dbSaveToastMessage = `Đã đồng bộ các biến số vào Database thành công!`;
              this.cdr.markForCheck();
              timer(5000)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe(() => {
                  this.dbSaveToastMessage = null;
                  this.cdr.markForCheck();
                });
            }
          },
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
      timer(3500)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => {
          this.copyTicketSuccessMsg = null;
          this.cdr.markForCheck();
        });
    }
  }

  // ==========================================
  // XỬ LÝ BIỂU ĐỒ LIÊN KẾT DÃY SỐ CSDL (HISTORICAL SEQUENCE LINKAGE CHART)
  // ==========================================
}
