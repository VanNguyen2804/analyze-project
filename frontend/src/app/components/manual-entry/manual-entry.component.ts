import { Component, OnInit } from '@angular/core';
import { LotteryService } from '../../services/lottery.service';
import { SavedLotteryRecord } from '../../models/lottery-number.model';

@Component({
  selector: 'app-manual-entry',
  templateUrl: './manual-entry.component.html',
  styleUrls: ['./manual-entry.component.css']
})
export class ManualEntryComponent implements OnInit {
  // Available grid numbers 1 to 45
  gridNumbers: number[] = Array.from({ length: 45 }, (_, i) => i + 1);

  // User manual inputs (6 slots)
  inputNumbers: (number | null)[] = [null, null, null, null, null, null];
  selectedSet: Set<number> = new Set<number>();

  note: string = '';
  isSaving: boolean = false;
  isLoadingList: boolean = false;
  successMessage: string | null = null;
  errorMessage: string | null = null;

  savedRecords: SavedLotteryRecord[] = [];

  constructor(private lotteryService: LotteryService) {}

  ngOnInit(): void {
    this.loadSavedRecords();
  }

  loadSavedRecords(): void {
    this.isLoadingList = true;
    this.lotteryService.getAll().subscribe({
      next: (records) => {
        this.savedRecords = records;
        this.isLoadingList = false;
      },
      error: (err) => {
        console.error('Lỗi khi tải dữ liệu từ DB H2:', err);
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
      if (val < 1 || val > 45) {
        this.errorMessage = `Số ${val} không hợp lệ! Vui lòng nhập từ 1 đến 45.`;
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
      if (num !== null && num >= 1 && num <= 45) {
        this.selectedSet.add(num);
      }
    }
  }

  randomPick(): void {
    this.errorMessage = null;
    this.successMessage = null;
    const set = new Set<number>();
    while (set.size < 6) {
      set.add(Math.floor(Math.random() * 45) + 1);
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

  saveToH2(): void {
    this.errorMessage = null;
    this.successMessage = null;

    if (this.selectedSet.size !== 6) {
      this.errorMessage = `Vui lòng chọn đúng 6 số khác nhau! (Hiện tại đã chọn: ${this.selectedSet.size}/6)`;
      return;
    }

    const numbersToSave = Array.from(this.selectedSet).sort((a, b) => a - b);
    this.isSaving = true;

    this.lotteryService.save({ numbers: numbersToSave, note: this.note.trim() }).subscribe({
      next: (saved) => {
        this.isSaving = false;
        this.successMessage = `Đã lưu thành công bộ 6 số vào cơ sở dữ liệu H2! (ID: ${saved.id})`;
        this.resetForm();
        this.loadSavedRecords();
      },
      error: (err) => {
        this.isSaving = false;
        this.errorMessage = err.error?.error || 'Lỗi khi lưu vào DB H2. Vui lòng thử lại!';
      }
    });
  }

  deleteRecord(id: number | string): void {
    if (!confirm('Bạn có chắc chắn muốn xóa bộ số này khỏi DB H2 không?')) {
      return;
    }

    this.lotteryService.delete(id).subscribe({
      next: () => {
        this.savedRecords = this.savedRecords.filter(r => r.id !== id);
        this.successMessage = 'Đã xóa bản ghi khỏi DB H2 thành công.';
      },
      error: (err) => {
        console.error('Lỗi khi xóa bản ghi:', err);
        this.errorMessage = 'Không thể xóa bản ghi.';
      }
    });
  }
}
