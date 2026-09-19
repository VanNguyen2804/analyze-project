import { Component, OnInit } from '@angular/core';
import { LotteryService } from '../../core/services/lottery.service';
import { SavedLotteryRecord } from '../../core/models/lottery-number.model';

@Component({
  selector: 'app-manual-entry',
  templateUrl: './manual-entry.component.html',
  styleUrls: ['./manual-entry.component.css']
})
export class ManualEntryComponent implements OnInit {
  // Category state: 'MEGA' (1-45, Wed/Fri/Sun) vs 'POWER' (1-55, Tue/Thu/Sat)
  selectedCategory: 'MEGA' | 'POWER' = 'MEGA';
  filterCategory: string = ''; // '' = all, 'MEGA', 'POWER'

  // Dynamic grid numbers based on category
  gridNumbers: number[] = [];

  // User manual inputs (6 slots)
  inputNumbers: (number | null)[] = [null, null, null, null, null, null];
  selectedSet: Set<number> = new Set<number>();

  // Date selection (default to today YYYY-MM-DD)
  selectedDate: string = this.getTodayDateString();
  filterDate: string = ''; // '' = all dates, or specific YYYY-MM-DD

  note: string = '';
  isSaving: boolean = false;
  isLoadingList: boolean = false;
  successMessage: string | null = null;
  errorMessage: string | null = null;

  savedRecords: SavedLotteryRecord[] = [];

  constructor(private lotteryService: LotteryService) {}

  ngOnInit(): void {
    this.updateCategoryFromDate(this.selectedDate);
    this.refreshGridNumbers();
    this.loadSavedRecords();
  }

  get maxLimit(): number {
    return this.selectedCategory === 'POWER' ? 55 : 45;
  }

  get dayOfWeekText(): string {
    if (!this.selectedDate) return '';
    const parts = this.selectedDate.split('-').map(Number);
    if (parts.length !== 3) return '';
    const dateObj = new Date(parts[0], parts[1] - 1, parts[2]);
    const day = dateObj.getDay();
    const dayNames = ['Chủ nhật', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];
    return dayNames[day] || '';
  }

  getTodayDateString(): string {
    const d = new Date();
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  setDateToday(): void {
    this.selectedDate = this.getTodayDateString();
    this.onDateChange();
  }

  setDateYesterday(): void {
    const d = new Date();
    d.setDate(d.getDate() - 1);
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    this.selectedDate = `${year}-${month}-${day}`;
    this.onDateChange();
  }

  onDateChange(): void {
    this.updateCategoryFromDate(this.selectedDate);
    this.refreshGridNumbers();
  }

  /**
   * Tự động nhận diện danh mục theo thứ trong tuần:
   * - Mega: Thứ 4, 6, Chủ nhật (1-45)
   * - Power: Thứ 3, 5, 7 (1-55)
   */
  updateCategoryFromDate(dateStr: string): void {
    if (!dateStr) return;
    const parts = dateStr.split('-').map(Number);
    if (parts.length === 3) {
      const dateObj = new Date(parts[0], parts[1] - 1, parts[2]);
      const day = dateObj.getDay(); // 0: Sun, 1: Mon, ..., 6: Sat
      if (day === 2 || day === 4 || day === 6) {
        this.selectedCategory = 'POWER';
      } else {
        this.selectedCategory = 'MEGA';
      }
    }
  }

  setCategory(cat: 'MEGA' | 'POWER'): void {
    if (this.selectedCategory !== cat) {
      this.selectedCategory = cat;
      this.refreshGridNumbers();
      // Remove any selected numbers that exceed new limit
      const currentSelected = Array.from(this.selectedSet);
      const filtered = currentSelected.filter(n => n <= this.maxLimit);
      if (filtered.length < currentSelected.length) {
        this.selectedSet = new Set(filtered);
        this.syncFromSet();
        this.errorMessage = `Đã tự động loại bỏ các số vượt quá giới hạn ${this.maxLimit} của danh mục ${cat}!`;
      }
    }
  }

  refreshGridNumbers(): void {
    this.gridNumbers = Array.from({ length: this.maxLimit }, (_, i) => i + 1);
  }

  loadSavedRecords(): void {
    this.isLoadingList = true;
    const queryDate = this.filterDate.trim() ? this.filterDate.trim() : undefined;
    const queryCategory = this.filterCategory.trim() ? this.filterCategory.trim() : undefined;

    this.lotteryService.getAll(queryDate, queryCategory).subscribe({
      next: (records: any[]) => {
        this.savedRecords = records;
        this.isLoadingList = false;
      },
      error: (err: any) => {
        console.error('Lỗi khi tải dữ liệu:', err);
        this.isLoadingList = false;
      }
    });
  }

  toggleGridNumber(num: number): void {
    this.errorMessage = null;
    this.successMessage = null;

    if (this.selectedSet.has(num)) {
      this.selectedSet.delete(num);
    } else {
      if (this.selectedSet.size >= 6) {
        this.errorMessage = 'Bạn đã chọn đủ 6 số! Hãy bấm lưu hoặc bỏ chọn bớt.';
        return;
      }
      this.selectedSet.add(num);
    }
    this.syncFromSet();
  }

  onInputChange(index: number, event: any): void {
    this.errorMessage = null;
    this.successMessage = null;
    const val = parseInt(event.target.value, 10);

    if (isNaN(val)) {
      this.inputNumbers[index] = null;
    } else {
      if (val < 1 || val > this.maxLimit) {
        this.errorMessage = `Số ${val} không hợp lệ! Với danh mục ${this.selectedCategory}, vui lòng nhập từ 1 đến ${this.maxLimit}.`;
        return;
      }
      this.inputNumbers[index] = val;
    }
    this.syncFromInputs();
  }

  syncFromSet(): void {
    const sorted = Array.from(this.selectedSet).sort((a, b) => a - b);
    for (let i = 0; i < 6; i++) {
      this.inputNumbers[i] = sorted[i] !== undefined ? sorted[i] : null;
    }
  }

  syncFromInputs(): void {
    this.selectedSet.clear();
    for (const num of this.inputNumbers) {
      if (num !== null && num >= 1 && num <= this.maxLimit) {
        this.selectedSet.add(num);
      }
    }
  }

  randomPick(): void {
    this.errorMessage = null;
    this.successMessage = null;
    const set = new Set<number>();
    while (set.size < 6) {
      set.add(Math.floor(Math.random() * this.maxLimit) + 1);
    }
    this.selectedSet = set;
    this.syncFromSet();
  }

  resetForm(): void {
    this.inputNumbers = [null, null, null, null, null, null];
    this.selectedSet.clear();
    this.note = '';
    this.errorMessage = null;
    this.successMessage = null;
  }

  saveNumbers(): void {
    this.errorMessage = null;
    this.successMessage = null;

    if (this.selectedSet.size !== 6) {
      this.errorMessage = `Vui lòng chọn đúng 6 số khác nhau! (Hiện tại đã chọn: ${this.selectedSet.size}/6)`;
      return;
    }

    if (!this.selectedDate) {
      this.errorMessage = 'Vui lòng chọn ngày mở thưởng/nhập số!';
      return;
    }

    const numbersToSave = Array.from(this.selectedSet).sort((a, b) => a - b);
    this.isSaving = true;

    this.lotteryService.save({
      numbers: numbersToSave,
      drawDate: this.selectedDate,
      category: this.selectedCategory,
      note: this.note.trim()
    }).subscribe({
      next: (saved: any) => {
        this.isSaving = false;
        this.successMessage = `Đã lưu thành công bộ 6 số ${saved.category} cho ngày ${saved.drawDate} (${this.dayOfWeekText})! (ID: #${saved.id})`;
        this.resetForm();
        this.loadSavedRecords();
      },
      error: (err: any) => {
        this.isSaving = false;
        this.errorMessage = err.error?.error || 'Lỗi khi lưu bộ số. Vui lòng thử lại!';
      }
    });
  }

  // Alias for backwards compatibility
  saveToH2(): void {
    this.saveNumbers();
  }

  deleteRecord(id: number | string): void {
    if (!confirm('Bạn có chắc chắn muốn xóa bộ số này không?')) {
      return;
    }

    this.lotteryService.delete(id).subscribe({
      next: () => {
        this.savedRecords = this.savedRecords.filter(r => r.id !== id);
        this.successMessage = 'Đã xóa bản ghi thành công.';
      },
      error: (err: any) => {
        console.error('Lỗi khi xóa bản ghi:', err);
        this.errorMessage = 'Không thể xóa bản ghi.';
      }
    });
  }
}
