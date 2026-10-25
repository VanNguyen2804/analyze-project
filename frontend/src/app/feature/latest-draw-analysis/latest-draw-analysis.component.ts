import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { AnalyzeService } from '../../core/services/analyze.service';
import { TicketItem, TicketAffinityDetail } from '../../core/models/prediction-payload.model';

export type { TicketItem, TicketAffinityDetail };

export interface EvaluatedUserTicket extends TicketItem {
  id?: number;
  numbers: number[];
  specialNumber?: number | null;
  matchedNumbers: number[];
  missedNumbers: number[];
  matchedCount: number;
  matchedSpecial: boolean;
  prize?: string;
  prizeAmount?: string;
  source: 'history' | 'manual';
  note?: string;
}

export interface MissedNumberReason {
  number: number;
  rank: number | null;
  probabilityPercent: number | null;
  frequency: number | null;
  drawGap: number | null;
  momentum: number | null;
  markov: number | null;
  poisson: number | null;
  companion: number | null;
  tag: string;
  title: string;
  reason: string;
}

export interface TuningReport {
  totalTickets: number;
  totalNumbersChecked: number;
  totalMatched: number; // Số bóng trúng DUY NHẤT (đã loại trừ trùng lặp giữa các vé)
  uniqueMatchedNumbers: number[];
  duplicateMatchedCount: number; // Tổng số lượt trúng thô trên tất cả các vé
  totalMissed: number;
  uniqueUserNumbersCount: number;
  officialWinningCount: number;
  hitRatePercent: number; // Tỉ lệ bao phủ số trúng chính thức: (uniqueMatchedCount / officialWinningCount) * 100
  selectionHitRatePercent: number; // Tỉ lệ trúng trên tập số chọn: (uniqueMatchedCount / uniqueUserNumbersCount) * 100
  averageRank: number | null;
  dominantMissFactor: string;
  keyDiagnoses: string[];
  tuningRecommendations: string[];
  hyperparametersJson: string;
  fullReportText: string;
}

@Component({
  selector: 'app-latest-draw-analysis',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './latest-draw-analysis.component.html',
  styleUrls: ['./latest-draw-analysis.component.css']
})
export class LatestDrawAnalysisComponent implements OnInit, OnDestroy {
  category: string = 'MEGA';
  selectedDate: string = '';
  algorithm: string = 'temporal_learning';
  
  availableAlgorithms: {id:string; name:string}[] = [];
  currentParameters: Record<string,unknown> | null = null;

  analysisData: any = null;
  isLoading: boolean = false;
  errorMessage: string | null = null;
  
  // User tickets state
  userTickets: EvaluatedUserTicket[] = [];
  isLoadingUserTickets: boolean = false;
  
  // Quick ticket input
  manualTicketInputs: (number | null)[] = [null, null, null, null, null, null];
  manualSpecialInput: number | null = null;
  manualInputError: string | null = null;
  quickSequenceInput: string = '';

  trackByIndex(index: number): number {
    return index;
  }

  get userTicketsSummary() {
    if (!this.userTickets || this.userTickets.length === 0) return null;
    const officialNumbers = (this.analysisData?.numbers || []).map(Number);
    const officialWinningCount = officialNumbers.length;

    const uniqueMatchedSet = new Set<number>();
    let rawTotalMatchedOccurrences = 0;

    for (const t of this.userTickets) {
      for (const m of (t.matchedNumbers || [])) {
        uniqueMatchedSet.add(Number(m));
        rawTotalMatchedOccurrences++;
      }
    }

    const uniqueMatchedNumbers = Array.from(uniqueMatchedSet).sort((a, b) => a - b);
    const uniqueMatchedCount = uniqueMatchedNumbers.length;

    // Tỉ lệ bao phủ số trúng chính thức: (Số bóng trúng DUY NHẤT / Tổng 6 bóng mở thưởng) * 100
    // Đã loại trừ trùng lặp: Nếu 2 vé có 3 số trúng và có số trùng nhau thì chỉ có 2 số trúng mà thôi -> Tỉ lệ: 2/6 = 33.3%
    const hitRatePercent = officialWinningCount > 0
      ? Math.round((uniqueMatchedCount / officialWinningCount) * 1000) / 10
      : 0;

    const uniqueUserNumbersSet = new Set<number>();
    for (const t of this.userTickets) {
      for (const n of (t.numbers || [])) {
        uniqueUserNumbersSet.add(Number(n));
      }
    }
    const uniqueUserNumbers = Array.from(uniqueUserNumbersSet).sort((a, b) => a - b);

    const uniqueMissedSet = new Set<number>();
    for (const t of this.userTickets) {
      for (const m of (t.missedNumbers || [])) {
        uniqueMissedSet.add(Number(m));
      }
    }
    const uniqueMissedNumbers = Array.from(uniqueMissedSet).sort((a, b) => a - b);

    const winningTicketsCount = this.userTickets.filter(
      (t) => t.prize && t.prize !== 'KHÔNG TRÚNG'
    ).length;

    return {
      totalTickets: this.userTickets.length,
      officialWinningCount,
      uniqueMatchedNumbers,
      uniqueMatchedCount,
      rawTotalMatchedOccurrences,
      duplicateCount: Math.max(0, rawTotalMatchedOccurrences - uniqueMatchedCount),
      hitRatePercent,
      uniqueUserNumbers,
      uniqueUserCount: uniqueUserNumbers.length,
      uniqueMissedNumbers,
      uniqueMissedCount: uniqueMissedNumbers.length,
      winningTicketsCount,
    };
  }

  // Cross-check & Tuning report
  missedNumberReasons: MissedNumberReason[] = [];
  tuningReport: TuningReport | null = null;
  copiedSuccess: boolean = false;

  // Hyperparameters Table & Update Algorithm State
  hyperparameterHistory: any[] = [];
  isLoadingHyperparameters: boolean = false;
  isUpdatingAlgorithm: boolean = false;
  algorithmUpdateMessage: string | null = null;
  selectedHyperparameterForView: any | null = null;
  activeModalTab: 'json' | 'readme' = 'json';

  // Backtesting & Reconciliation State
  reconciliationData: any = null;
  isLoadingReconciliation: boolean = false;
  reconcileError: string | null = null;
  selected5DrawIndex: number = 0;
  activeReconciliationTab: 'aiPredictionJudgment' | 'comparison' | 'winningBalls' | 'algorithmFlaws' | 'tuningPlan' | 'affinityNetwork' = 'aiPredictionJudgment';
  reconcileLimit: number = 5;
  reconcileAlgorithm: string = 'temporal_learning';
  selectedReconcileDate: string = '';
  availableLimits: number[] = [];
  showAllBacktestTable: boolean = false;
  isApplyingV150: boolean = false;

  getLimitLabel(lim: number): string {
    return lim === this.reconciliationData?.totalDrawsInDb ? `All (${lim})` : String(lim);
  }

  // Recommendations & Hyperparameters State
  recommendationsData: any = null;
  currentHyperparameterVersion: string = '';

  private categorySub: Subscription | undefined;

  constructor(
    private analyzeService: AnalyzeService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.analyzeService.getAlgorithms().subscribe({
      next: catalog => { this.availableAlgorithms = catalog.map(item => ({id:item.id,name:item.name})); this.cdr.markForCheck(); },
      error: () => { this.errorMessage = 'Cannot load algorithm catalog'; this.cdr.markForCheck(); }
    });
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      if (newCategory) {
        this.category = newCategory;
        this.fetchAnalysis(true);
        this.loadHyperparameters();
        this.load5DrawsReconciliation();
        this.loadRecommendations();
      }
    });
  }

  loadRecommendations(): void {
    this.analyzeService.getPrediction(this.category, this.algorithm).subscribe({
      next: (res) => {
        if (res?.recommendations || res?.aiRecommendation) {
          this.recommendationsData = res.recommendations || res.aiRecommendation;
        }
        if (res?.hyperparameterVersion) {
          this.currentHyperparameterVersion = res.hyperparameterVersion;
        }
        this.cdr.markForCheck();
      },
      error: (err) => console.error('Error loading recommendations:', err)
    });
  }

  ngOnDestroy(): void {
    if (this.categorySub) this.categorySub.unsubscribe();
  }

  changeCategory(cat: string) {
    this.category = cat;
    this.analyzeService.setCategory(cat);
    this.fetchAnalysis(true);
    this.cdr.markForCheck();
  }

  fetchAnalysis(useLatest: boolean = false): void {
    this.isLoading = true;
    this.errorMessage = null;

    if (useLatest) {
      this.selectedDate = ''; // Lấy ngày mới nhất từ DB
    }
    this.cdr.markForCheck();

    // Gửi đúng 3 tham số
    this.analyzeService.getOfficialDrawAnalysis(this.category, this.selectedDate, this.algorithm).subscribe({
      next: (response) => {
        this.analysisData = response;
        if (response && response.drawDate) {
          this.selectedDate = response.drawDate; // Map lại ngày trả về lên UI
        }
        this.isLoading = false;
        this.loadUserTicketsForDraw();
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.errorMessage = 'Không tìm thấy dữ liệu phân tích cho ngày/loại vé này.';
        this.isLoading = false;
        this.analysisData = null;
        this.userTickets = [];
        this.missedNumberReasons = [];
        this.tuningReport = null;
        this.cdr.markForCheck();
      }
    });
  }

  loadUserTicketsForDraw(): void {
    if (!this.analysisData || !this.analysisData.numbers) return;
    this.isLoadingUserTickets = true;
    this.cdr.markForCheck();

    this.analyzeService.getUserHistory().subscribe({
      next: (history) => {
        this.isLoadingUserTickets = false;
        const allHistory = Array.isArray(history) ? history : [];
        const normSelectedDate = this.selectedDate.trim();

        // Lọc vé của user khớp loại category và drawDate
        const matchingHistory = allHistory.filter((t: any) => {
          const tCat = (t.category || '').toUpperCase();
          const tDate = (t.drawDate || '').trim();
          return tCat === this.category.toUpperCase() && (tDate === normSelectedDate || !normSelectedDate);
        });

        const evaluated: EvaluatedUserTicket[] = [];
        for (const t of matchingHistory) {
          const validNums = (t.numbers || []).filter((n: any) => Number(n) > 0).map(Number);
          if (validNums.length === 6) {
            evaluated.push(this.evaluateUserTicket(validNums, t.specialNumber, 'history', t.id));
          }
        }

        this.userTickets = evaluated;
        this.buildMissedAnalysisAndReport();
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingUserTickets = false;
        this.userTickets = [];
        this.buildMissedAnalysisAndReport();
        this.cdr.markForCheck();
      }
    });
  }

  evaluateUserTicket(nums: number[], special: number | null | undefined, source: 'history' | 'manual', id?: number): EvaluatedUserTicket {
    const winning = this.analysisData?.numbers || [];
    const officialSpecial = this.analysisData?.specialNumber;
    
    const matched = nums.filter(n => winning.includes(n));
    const missed = nums.filter(n => !winning.includes(n));
    const matchedCount = matched.length;

    let matchedSpecial = false;
    if (this.category === 'POWER' && officialSpecial !== undefined && officialSpecial !== null) {
      if (special !== undefined && special !== null) {
        matchedSpecial = Number(special) === Number(officialSpecial);
      } else {
        matchedSpecial = nums.includes(Number(officialSpecial));
      }
    }

    let prize = 'KHÔNG TRÚNG';
    let prizeAmount = '0 đ';

    if (this.category === 'MEGA') {
      if (matchedCount === 6) { prize = 'JACKPOT'; prizeAmount = '≥ 12 Tỷ'; }
      else if (matchedCount === 5) { prize = 'GIẢI NHẤT'; prizeAmount = '10.000.000 đ'; }
      else if (matchedCount === 4) { prize = 'GIẢI NHÌ'; prizeAmount = '300.000 đ'; }
      else if (matchedCount === 3) { prize = 'GIẢI BA'; prizeAmount = '30.000 đ'; }
    } else {
      if (matchedCount === 6) { prize = 'JACKPOT 1'; prizeAmount = '≥ 30 Tỷ'; }
      else if (matchedCount === 5 && matchedSpecial) { prize = 'JACKPOT 2'; prizeAmount = '≥ 3 Tỷ'; }
      else if (matchedCount === 5) { prize = 'GIẢI NHẤT'; prizeAmount = '40.000.000 đ'; }
      else if (matchedCount === 4) { prize = 'GIẢI NHÌ'; prizeAmount = '500.000 đ'; }
      else if (matchedCount === 3) { prize = 'GIẢI BA'; prizeAmount = '50.000 đ'; }
    }

    return {
      id,
      numbers: [...nums].sort((a, b) => a - b),
      specialNumber: special,
      matchedNumbers: matched,
      missedNumbers: missed,
      matchedCount,
      matchedSpecial,
      prize,
      prizeAmount,
      source
    };
  }

  onQuickSequenceChange(val: string): void {
    if (!val) return;
    this.manualInputError = null;
    const maxLimit = this.category === 'POWER' ? 55 : 45;
    // Tách các số từ chuỗi nhập vào (phân tách bởi dấu phẩy, khoảng trắng, gạch ngang, chấm phẩy)
    const matches = val.match(/\d+/g);
    if (matches && matches.length > 0) {
      let idx = 0;
      for (const m of matches) {
        const num = parseInt(m, 10);
        if (num >= 1 && num <= maxLimit) {
          if (idx < 6) {
            this.manualTicketInputs[idx] = num;
            idx++;
          } else if (idx === 6 && this.category === 'POWER' && !this.manualSpecialInput) {
            this.manualSpecialInput = num;
            idx++;
          }
        }
      }
    }
    this.cdr.markForCheck();
  }

  onBallInput(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const val = input.value.trim();
    const maxLimit = this.category === 'POWER' ? 55 : 45;

    // Nếu người dùng dán hoặc gõ nhiều số trong 1 ô tròn
    if (val.length > 2 && /\s|,|-|;/.test(val)) {
      this.onQuickSequenceChange(val);
      return;
    }

    const num = parseInt(val, 10);
    if (!isNaN(num)) {
      if (num > maxLimit) {
        this.manualTicketInputs[index] = maxLimit;
      } else if (num < 1) {
        this.manualTicketInputs[index] = null;
      } else {
        this.manualTicketInputs[index] = num;
      }
    } else {
      this.manualTicketInputs[index] = null;
    }

    // Tự động chuyển con trỏ sang ô bóng tiếp theo khi đã nhập đủ 2 chữ số hoặc số >= 10
    if (val.length >= 2 || (num >= 10 && num <= maxLimit)) {
      if (index < 5) {
        const nextEl = document.getElementById(`manual-ball-${index + 1}`) as HTMLInputElement;
        if (nextEl) {
          nextEl.focus();
          nextEl.select();
        }
      } else if (index === 5 && this.category === 'POWER') {
        const specialEl = document.getElementById('manual-special-ball') as HTMLInputElement;
        if (specialEl) {
          specialEl.focus();
          specialEl.select();
        }
      }
    }
    this.cdr.markForCheck();
  }

  onPasteIntoBalls(event: ClipboardEvent): void {
    const text = event.clipboardData?.getData('text');
    if (text) {
      event.preventDefault();
      this.onQuickSequenceChange(text);
    }
  }

  resetManualInputs(): void {
    this.manualTicketInputs = [null, null, null, null, null, null];
    this.manualSpecialInput = null;
    this.quickSequenceInput = '';
    this.manualInputError = null;
    this.cdr.markForCheck();
  }

  addManualTicket(): void {
    this.manualInputError = null;
    const maxLimit = this.category === 'POWER' ? 55 : 45;
    const nums: number[] = [];

    for (let i = 0; i < 6; i++) {
      const val = Number(this.manualTicketInputs[i]);
      if (!val || isNaN(val) || val < 1 || val > maxLimit) {
        this.manualInputError = `Vui lòng nhập đủ 6 số hợp lệ từ 1 đến ${maxLimit}!`;
        this.cdr.markForCheck();
        return;
      }
      nums.push(val);
    }

    const uniqueSet = new Set(nums);
    if (uniqueSet.size !== 6) {
      this.manualInputError = 'Các số trong vé không được trùng lặp!';
      this.cdr.markForCheck();
      return;
    }

    let spec: number | null = null;
    if (this.category === 'POWER' && this.manualSpecialInput) {
      spec = Number(this.manualSpecialInput);
    }

    const evaluated = this.evaluateUserTicket(nums, spec, 'manual');
    this.userTickets.unshift(evaluated);

    // Reset inputs
    this.manualTicketInputs = [null, null, null, null, null, null];
    this.manualSpecialInput = null;
    this.quickSequenceInput = '';

    this.buildMissedAnalysisAndReport();
    this.cdr.markForCheck();
  }

  removeTicket(index: number): void {
    this.userTickets.splice(index, 1);
    this.buildMissedAnalysisAndReport();
    this.cdr.markForCheck();
  }

  buildMissedAnalysisAndReport(): void {
    if (!this.analysisData || !this.analysisData.numbers) {
      this.missedNumberReasons = [];
      this.tuningReport = null;
      return;
    }

    const maxLimit = this.category === 'POWER' ? 55 : 45;
    const allDetails = this.analysisData.allNumberDetails || {};
    const winningNumbers: number[] = this.analysisData.numbers || [];

    // Tập hợp toàn bộ số trượt từ các vé của user (loại bỏ trùng lặp để phân tích chi tiết)
    const uniqueMissed = new Set<number>();
    for (const t of this.userTickets) {
      for (const m of t.missedNumbers) {
        uniqueMissed.add(m);
      }
    }

    const missedList = Array.from(uniqueMissed).sort((a, b) => a - b);
    const missedReasons: MissedNumberReason[] = [];

    const measured = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null;
    for (const num of missedList) {
      const detail = allDetails[num];
      missedReasons.push({
        number: num, rank: measured(detail?.rank), probabilityPercent: measured(detail?.probabilityPercent),
        frequency: measured(detail?.frequency), drawGap: measured(detail?.drawGap), momentum: measured(detail?.momentum),
        markov: measured(detail?.markov), poisson: measured(detail?.poisson), companion: measured(detail?.companion),
        tag: detail?.tag ?? 'CHƯA CÓ SỐ LIỆU', title: detail?.title ?? 'Đối chiếu kết quả',
        reason: detail?.reasonNotDrawn ?? detail?.reason ?? `Số ${num} không nằm trong kết quả kỳ ${this.selectedDate}; chưa có thống kê để giải thích thêm.`
      });
    }

    this.missedNumberReasons = missedReasons;

    // Build Tuning Report
    const totalTickets = this.userTickets.length;
    if (totalTickets === 0) {
      this.tuningReport = null;
      return;
    }

    // 1. TẬP HỢP TẤT CẢ CÁC SỐ TRÚNG DUY NHẤT (LOẠI BỎ HOÀN TOÀN TRÙNG LẶP NẾU 2 VÉ CÓ CÙNG SỐ TRÚNG)
    const uniqueMatchedSet = new Set<number>();
    let rawTotalMatchedOccurrences = 0;
    for (const t of this.userTickets) {
      for (const m of (t.matchedNumbers || [])) {
        uniqueMatchedSet.add(m);
        rawTotalMatchedOccurrences++;
      }
    }
    const uniqueMatchedNumbers = Array.from(uniqueMatchedSet).sort((a, b) => a - b);
    const uniqueMatchedCount = uniqueMatchedNumbers.length; // Số bóng trúng DUY NHẤT (không tính trùng)

    // 2. TẬP HỢP TẤT CẢ CÁC SỐ NGƯỜI DÙNG ĐÃ CHỌN TRONG CÁC VÉ (DUY NHẤT)
    const uniqueUserNumbersSet = new Set<number>();
    for (const t of this.userTickets) {
      for (const n of (t.numbers || [])) {
        uniqueUserNumbersSet.add(n);
      }
    }
    const uniqueUserNumbers = Array.from(uniqueUserNumbersSet).sort((a, b) => a - b);
    const uniqueUserNumbersCount = uniqueUserNumbers.length;

    // 3. TẬP HỢP CÁC SỐ TRƯỢT DUY NHẤT
    const uniqueMissedSet = new Set<number>();
    for (const t of this.userTickets) {
      for (const m of (t.missedNumbers || [])) {
        uniqueMissedSet.add(m);
      }
    }
    const totalMissed = uniqueMissedSet.size;
    const totalNumbersChecked = totalTickets * 6;

    // 4. SỐ BÓNG MỞ THƯỞNG CHÍNH THỨC CỦA KỲ QUAY (thường là 6)
    const officialWinningCount = winningNumbers.length;

    // Tỉ lệ bao phủ số trúng chính thức: (Số bóng trúng duy nhất / Tổng 6 bóng mở thưởng) * 100
    // Ví dụ: 2 vé có 3 lượt trúng nhưng trùng nhau 1 số -> có 2 số trúng duy nhất -> 2 / 6 = 33.3%
    const hitRatePercent = officialWinningCount > 0
      ? Math.round((uniqueMatchedCount / officialWinningCount) * 1000) / 10
      : 0;

    // Tỉ lệ trúng trên tổng số bóng độc nhất đã chọn (Selection Hit Rate)
    const selectionHitRatePercent = uniqueUserNumbersCount > 0
      ? Math.round((uniqueMatchedCount / uniqueUserNumbersCount) * 1000) / 10
      : 0;

    const measuredRanks = missedReasons.map(item => item.rank).filter((rank): rank is number => rank != null);
    const avgRank = measuredRanks.length ? Math.round(measuredRanks.reduce((sum,rank)=>sum+rank,0)/measuredRanks.length*10)/10 : null;
    const dominantFactor = `${uniqueMatchedCount}/${officialWinningCount} số kết quả được phủ bởi ${totalTickets} vé; ${totalMissed} số đã chọn không khớp.`;
    const keyDiagnoses = [
      dominantFactor,
      `Có ${rawTotalMatchedOccurrences} lượt khớp, tương ứng ${uniqueMatchedCount} số kết quả khác nhau.`,
      `Có xếp hạng từ API cho ${measuredRanks.length}/${missedList.length} số không khớp.`
    ];
    const tuningRecommendations = [
      'Đánh giá nhiều kỳ với cùng số vé trước khi chọn tham số mới.',
      `Cấu hình đang lưu: ${this.currentHyperparameterVersion || 'chưa có phiên bản'}.`
    ];
    const hyperParams = {
      ...(this.currentParameters ?? {}),
      evaluationSummary: { category: this.category, drawDate: this.selectedDate, totalTickets, hitRatePercent,
        matchedCount: uniqueMatchedCount, uniqueMatchedNumbers, duplicateMatchedCount: rawTotalMatchedOccurrences,
        uniqueUserNumbersCount, missedCount: totalMissed, averageMissedRank: avgRank }
    };

    const hyperJson = JSON.stringify(hyperParams, null, 2);

    const fullReportText = `=== BÁO CÁO ĐỐI CHIẾU VÉ & KẾT LUẬN CẬP NHẬT THUẬT TOÁN ===
Kỳ quay: Vietlott ${this.category === 'POWER' ? 'Power 6/55' : 'Mega 6/45'} - Ngày: ${this.selectedDate}
Thuật toán phân tích: ${this.analysisData.algorithmName || this.algorithm}
Kết quả mở thưởng chính thức: [ ${winningNumbers.join(' - ')} ] ${this.category === 'POWER' && this.analysisData.specialNumber ? `(Banh phụ: ${this.analysisData.specialNumber})` : ''}

1. KẾT QUẢ ĐỐI SOÁT VÉ CỦA USER:
- Tổng số vé đã kiểm tra: ${totalTickets} vé (${uniqueUserNumbersCount} số độc nhất đã chọn)
- Số bóng trúng duy nhất: ${uniqueMatchedCount}/${officialWinningCount} bóng chính thức (${hitRatePercent}%) [ ${uniqueMatchedNumbers.join(', ') || 'Không'} ]${(rawTotalMatchedOccurrences > uniqueMatchedCount) ? ` (Đã lọc trùng từ ${rawTotalMatchedOccurrences} lượt trúng trên các vé)` : ''}
- Số bóng trượt độc nhất: ${totalMissed} bóng
- Chi tiết từng vé:
${this.userTickets.map((t, idx) => `  * Vé #${idx + 1}: [ ${t.numbers.join(', ')} ] -> Khớp: [ ${t.matchedNumbers.join(', ') || 'Không'} ] (${t.matchedCount}/6) | Giải: ${t.prize}`).join('\n')}

2. ĐỐI CHIẾU THUẬT TOÁN VÌ SAO CÁC SỐ CỦA USER KHÔNG RA:
${missedReasons.map(m => `  * Số [ ${m.number < 10 ? '0' + m.number : m.number} ] (Hạng #${m.rank}/${maxLimit} - Xác suất ${m.probabilityPercent}% - Tag: ${m.tag}):
    -> ${m.reason}`).join('\n')}

3. KẾT LUẬN CHẨN ĐOÁN SAI SỐ:
- Điểm lệch chủ đạo: ${dominantFactor}
- Xếp hạng trung bình của các số trượt: #${avgRank}/${maxLimit}
- Cấu trúc kỳ quay thực tế: Tổng = ${this.analysisData.sum || 'N/A'}, Tỷ lệ = ${this.analysisData.oddEvenRatio || 'Cân bằng'}

4. THAM SỐ KHUYẾN NGHỊ CẬP NHẬT THUẬT TOÁN (HYPERPARAMETERS JSON):
${hyperJson}
==========================================================`;

    this.tuningReport = {
      totalTickets,
      totalNumbersChecked,
      totalMatched: uniqueMatchedCount,
      uniqueMatchedNumbers,
      duplicateMatchedCount: rawTotalMatchedOccurrences,
      totalMissed,
      uniqueUserNumbersCount,
      officialWinningCount,
      hitRatePercent,
      selectionHitRatePercent,
      averageRank: avgRank,
      dominantMissFactor: dominantFactor,
      keyDiagnoses,
      tuningRecommendations,
      hyperparametersJson: hyperJson,
      fullReportText
    };
  }

  copyConclusion(): void {
    if (!this.tuningReport?.fullReportText) return;

    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(this.tuningReport.fullReportText).then(() => {
        this.copiedSuccess = true;
        this.cdr.markForCheck();
        setTimeout(() => {
          this.copiedSuccess = false;
          this.cdr.markForCheck();
        }, 3000);
      }).catch(() => {
        this.fallbackCopy(this.tuningReport!.fullReportText);
      });
    } else {
      this.fallbackCopy(this.tuningReport.fullReportText);
    }
  }

  private fallbackCopy(text: string): void {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();
    try {
      document.execCommand('copy');
      this.copiedSuccess = true;
      this.cdr.markForCheck();
      setTimeout(() => {
        this.copiedSuccess = false;
        this.cdr.markForCheck();
      }, 3000);
    } catch (e) {
      console.error('Copy failed:', e);
    }
    document.body.removeChild(textarea);
  }

  // =========================================================================================
  // QUẢN LÝ BẢNG SIÊU THAM SỐ THUẬT TOÁN (ALGORITHM HYPERPARAMETERS TABLE)
  // =========================================================================================
  loadHyperparameters(): void {
    this.analyzeService.getLatestHyperparameters(this.category).subscribe({
      next: item => {
        this.currentHyperparameterVersion = item?.version ?? '';
        try { this.currentParameters = item ? JSON.parse(item.hyperparametersJson ?? '{}') : null; }
        catch { this.currentParameters = null; }
        if (this.analysisData) this.buildMissedAnalysisAndReport();
        this.cdr.markForCheck();
      },
      error: () => { this.currentParameters = null; this.currentHyperparameterVersion = ''; this.cdr.markForCheck(); }
    });
    this.isLoadingHyperparameters = true;
    this.cdr.markForCheck();
    this.analyzeService.getHyperparameters(this.category).subscribe({
      next: (list) => {
        this.hyperparameterHistory = list || [];
        this.isLoadingHyperparameters = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load hyperparameters:', err);
        this.isLoadingHyperparameters = false;
        this.cdr.markForCheck();
      }
    });
  }

  updateAlgorithmAndSaveTable(): void {
    if (!this.tuningReport?.hyperparametersJson || !this.currentParameters) return;
    this.isUpdatingAlgorithm = true;
    this.algorithmUpdateMessage = null;
    this.cdr.markForCheck();

    const payload = {
      hyperparametersJson: this.tuningReport.hyperparametersJson,
      category: this.category,
      drawDate: this.selectedDate,
      readmeContent: this.tuningReport.fullReportText,
      note: `Lưu cấu hình ${this.algorithm} kèm đối soát kỳ ${this.selectedDate} (${this.category})`
    };

    this.analyzeService.updateAlgorithm(payload).subscribe({
      next: (res: any) => {
        this.isUpdatingAlgorithm = false;
        const version = res.record?.version || res.version || 'mới';
        this.algorithmUpdateMessage = `✅ Cập nhật thuật toán thành công! Đã lưu phiên bản ${version} kèm tài liệu README vào bảng hyperparameters lúc ${new Date().toLocaleTimeString('vi-VN')}.`;
        this.loadHyperparameters();
        this.cdr.markForCheck();
        setTimeout(() => {
          this.algorithmUpdateMessage = null;
          this.cdr.markForCheck();
        }, 6000);
      },
      error: (err: any) => {
        this.isUpdatingAlgorithm = false;
        this.algorithmUpdateMessage = '❌ Lỗi khi cập nhật thuật toán: ' + (err.message || 'Lỗi kết nối máy chủ');
        this.cdr.markForCheck();
      }
    });
  }

  activateHyperparameter(id: number): void {
    this.analyzeService.activateHyperparameter(id).subscribe({
      next: (res: any) => {
        const ver = res.record?.version || res.version || '';
        this.algorithmUpdateMessage = `✅ Đã kích hoạt lại phiên bản siêu tham số ${ver} cho thuật toán!`;
        this.loadHyperparameters();
        this.cdr.markForCheck();
        setTimeout(() => {
          this.algorithmUpdateMessage = null;
          this.cdr.markForCheck();
        }, 5000);
      },
      error: (err: any) => {
        console.error('Failed to activate hyperparameter:', err);
        this.algorithmUpdateMessage = '❌ Lỗi khi kích hoạt phiên bản';
        this.cdr.markForCheck();
      }
    });
  }

  viewHyperparameterJson(item: any, tab: 'json' | 'readme' = 'json'): void {
    this.selectedHyperparameterForView = item;
    this.activeModalTab = tab;
    this.cdr.markForCheck();
  }

  closeHyperparameterModal(): void {
    this.selectedHyperparameterForView = null;
    this.cdr.markForCheck();
  }

  // =========================================================================================
  // BACKTESTING CÁC KỲ TRƯỚC & ĐỐI SOÁT DỰ ĐOÁN AI (WALK-FORWARD TESTING)
  // =========================================================================================
  load5DrawsReconciliation(): void {
    this.isLoadingReconciliation = true;
    this.reconcileError = null;
    this.cdr.markForCheck();
    this.analyzeService.get5DrawsReconciliation(
      this.category,
      this.reconcileAlgorithm,
      this.reconcileLimit,
      this.selectedReconcileDate
    ).subscribe({
      next: (data) => {
        this.reconciliationData = data;
        const count = Math.min(data?.totalDrawsInDb ?? 0,100);
        this.availableLimits = [...new Set([5,10,20,50,count].filter(n=>n>0 && n<=count))];
        if (data?.recommendations) {
          this.recommendationsData = data.recommendations;
        }
        if (data?.currentHyperparameterVersion) {
          this.currentHyperparameterVersion = data.currentHyperparameterVersion;
        }
        if (this.selectedReconcileDate && data?.draws) {
          const idx = data.draws.findIndex((d: any) => d.drawDate === this.selectedReconcileDate);
          this.selected5DrawIndex = idx !== -1 ? idx : 0;
        } else if (this.selected5DrawIndex >= (data?.draws?.length || 0)) {
          this.selected5DrawIndex = 0;
        }
        this.isLoadingReconciliation = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load reconciliation backtest:', err);
        this.reconcileError = 'Không thể kết nối máy chủ để tính toán kiểm thử. Vui lòng bấm thử lại!';
        this.isLoadingReconciliation = false;
        this.cdr.markForCheck();
      }
    });
  }

  setReconcileLimit(lim: number): void {
    if (this.reconcileLimit === lim) return;
    this.reconcileLimit = lim;
    this.load5DrawsReconciliation();
  }

  setReconcileAlgorithm(alg: string): void {
    if (this.reconcileAlgorithm === alg) return;
    this.reconcileAlgorithm = alg;
    this.load5DrawsReconciliation();
  }

  onReconcileDateChange(date: string): void {
    this.selectedReconcileDate = date;
    if (!date) {
      this.load5DrawsReconciliation();
      return;
    }
    if (this.reconciliationData?.draws) {
      const idx = this.reconciliationData.draws.findIndex((d: any) => d.drawDate === date);
      if (idx !== -1) {
        this.selected5DrawIndex = idx;
        this.cdr.markForCheck();
        return;
      }
    }
    this.load5DrawsReconciliation();
  }

  select5Draw(index: number): void {
    this.selected5DrawIndex = index;
    if (this.reconciliationData?.draws?.[index]) {
      this.selectedReconcileDate = this.reconciliationData.draws[index].drawDate;
    }
    this.cdr.markForCheck();
  }

  getCurrent5Draw(): any {
    if (!this.reconciliationData?.draws || this.reconciliationData.draws.length === 0) return null;
    return this.reconciliationData.draws[this.selected5DrawIndex] || this.reconciliationData.draws[0];
  }

  getAffinityTickets(draw: any): TicketAffinityDetail[] {
    const list = draw?.aiPrediction?.numberRelationships?.ticketAffinityDetails;
    if (!Array.isArray(list)) return [];
    return list.slice(0, 10);
  }

  isNearMiss(predictedNum: number, officialNums: number[]): boolean {
    if (!officialNums || officialNums.includes(predictedNum)) return false;
    return officialNums.some(o => Math.abs(o - predictedNum) === 1);
  }

  getNearMissTarget(predictedNum: number, officialNums: number[]): number | null {
    if (!officialNums) return null;
    const target = officialNums.find(o => Math.abs(o - predictedNum) === 1);
    return target !== undefined ? target : null;
  }

  applyV150Tuning(): void {
    if (!this.reconciliationData?.recommendedHyperparameters?.adjustments) return;
    const hyp = this.reconciliationData.recommendedHyperparameters;
    const targetVer = hyp.version || this.currentHyperparameterVersion;
    const testedDates = (this.reconciliationData?.draws || []).map((d: any) => d.drawDate).join(', ');
    this.isApplyingV150 = true;
    this.algorithmUpdateMessage = null;
    this.cdr.markForCheck();

    const readmeContent = `# Báo Cáo Hiệu Chỉnh Thuật Toán ${targetVer} (Khắc Phục Sai Lệch Các Kỳ Gần Nhất)

## 1. Bối cảnh & Dữ liệu Đối soát Thực Tế (Các Kỳ: ${testedDates})
- Tổng số vé đối chiếu thực tế: ${this.reconciliationData.overallSummary?.totalTickets || 0} vé
- Số vé trúng thưởng: ${this.reconciliationData.overallSummary?.winningTickets || 0} vé
- Số vé trượt: ${this.reconciliationData.overallSummary?.missedTickets || 0} vé
- Tỷ lệ khớp: ${this.reconciliationData.overallSummary?.hitRatePercent || 0}%

## 2. Các Nguyên Nhân Cốt Lõi Gây Sai Lệch Kết Quả:
${this.reconciliationData.overallSummary?.dominantFlaws?.map((f: string, i: number) => `${i + 1}. ${f}`).join('\n') || ''}

## 3. Các Hiệu Chỉnh Tham Số Trọng Yếu Trong Phiên Bản ${targetVer}:
${JSON.stringify(hyp.adjustments, null, 2)}`;

    const payload = {
      hyperparametersJson: JSON.stringify(hyp.adjustments, null, 2),
      category: this.category,
      drawDate: hyp.drawDate || this.selectedDate || new Date().toISOString().split('T')[0],
      readmeContent: readmeContent,
      note: `Hiệu chỉnh thuật toán ${targetVer}: Khắc phục sai lệch theo đối soát các kỳ quay gần nhất (${this.category})`
    };

    this.analyzeService.updateAlgorithm(payload).subscribe({
      next: (res: any) => {
        this.isApplyingV150 = false;
        const version = res.record?.version || res.version || targetVer;
        this.currentHyperparameterVersion = version;
        this.algorithmUpdateMessage = `✅ Đã áp dụng thành công bộ tham số ${version} khắc phục sai lệch vào hệ thống!`;
        this.loadHyperparameters();
        this.cdr.markForCheck();
        setTimeout(() => {
          this.algorithmUpdateMessage = null;
          this.cdr.markForCheck();
        }, 6000);
      },
      error: (err: any) => {
        this.isApplyingV150 = false;
        this.algorithmUpdateMessage = '❌ Lỗi khi cập nhật thuật toán: ' + (err.message || 'Lỗi server');
        this.cdr.markForCheck();
      }
    });
  }

  formatMetric(value: number | null | undefined, suffix = ''): string {
    return value == null ? 'N/A' : `${value}${suffix}`;
  }

  formatNumber(num: number | null | undefined): string {
    if (num === null || num === undefined) return '--';
    return num < 10 ? '0' + num : num.toString();
  }

  formatNumbers(nums?: number[]): string {
    if (!nums || !nums.length) return '';
    return nums.map(n => this.formatNumber(n)).join(', ');
  }
}
