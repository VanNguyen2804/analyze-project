import {
  Component,
  OnInit,
  ChangeDetectorRef,
  inject,
  DestroyRef,
  ChangeDetectionStrategy,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgClass, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { distinctUntilChanged } from 'rxjs';
import { AnalyzeService } from '@app/core/services/analyze.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-entry',
  standalone: true,
  imports: [FormsModule, DatePipe, NgClass],
  templateUrl: './entry.component.html',
  styleUrl: './entry.component.css',
})
export class EntryComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly analyzeService = inject(AnalyzeService);
  private readonly cdr = inject(ChangeDetectorRef);

  category: string = 'MEGA';
  drawDate: string = new Date().toISOString().split('T')[0];

  // Dãy số kết quả Vietlott chính thức theo ngày
  officialNumbers: (number | null)[] = [null, null, null, null, null, null];
  officialSpecialNumber: number | null = null;
  isSavingOfficial: boolean = false;
  saveOfficialMessage: string = '';
  existingOfficialDraw: any = null;

  // Dãy số vé cá nhân để dò
  userTickets: (number | null)[][] = [Array<number | null>(6).fill(null)];

  checkResult: any = null;
  isChecking = false;

  recentDraws: any[] = [];
  userCheckHistory: any[] = [];
  isLoadingHistory: boolean = false;

  editingDrawId: number | null = null;
  editDrawDate: string = '';
  editNumbers: number[] = [];
  editSpecialNumber: number | null = null;
  editingOfficialDrawId: number | null = null;

  ngOnInit() {
    // Lắng nghe sự thay đổi Category từ Header
    this.analyzeService.currentCategory$
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((newCategory) => {
        if (newCategory) {
          this.category = newCategory;
        }
        this.loadHistory(); // Tự động tải lại bảng lịch sử Database
      });

    this.loadUserHistory();
  }

  // Hàm đồng bộ ngược lên Header khi đổi Category tại Dropdown của trang này
  onCategoryChange(newCategory: string) {
    if (newCategory) {
      this.category = newCategory;
      this.analyzeService.setCategory(newCategory);
      this.loadHistory();
      this.cdr.markForCheck();
    }
  }

  onDateOrCategoryChange() {
    if (!this.editingOfficialDrawId) {
      this.checkExistingOfficialDraw();
    }
    this.cdr.markForCheck();
  }

  loadDrawToMainForm(draw: any) {
    this.editingOfficialDrawId = draw.id;
    this.drawDate = draw.drawDate;
    this.category = draw.category || this.category;
    this.officialNumbers = draw.numbers ? [...draw.numbers] : [null, null, null, null, null, null];
    this.officialSpecialNumber = draw.specialNumber ?? null;
    this.existingOfficialDraw = draw;
    window.scrollTo({ top: 0, behavior: 'smooth' });
    this.cdr.markForCheck();
  }

  cancelOfficialEdit() {
    this.editingOfficialDrawId = null;
    this.checkExistingOfficialDraw();
    this.cdr.markForCheck();
  }

  loadHistory() {
    this.isLoadingHistory = true;
    this.cdr.markForCheck();
    this.analyzeService
      .getHistory(this.category)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.isLoadingHistory = false;
          this.recentDraws = Array.isArray(res) ? res : [];
          this.checkExistingOfficialDraw();
          this.cdr.markForCheck();
        },
        error: (_err) => {
          this.isLoadingHistory = false;
          console.error('Lỗi tải lịch sử database:', _err);
          this.recentDraws = [];
          this.cdr.markForCheck();
        },
      });
  }

  checkExistingOfficialDraw() {
    if (!this.recentDraws || !Array.isArray(this.recentDraws)) {
      this.existingOfficialDraw = null;
      return;
    }
    const existing = this.recentDraws.find((d: any) => d.drawDate === this.drawDate);
    if (existing) {
      this.existingOfficialDraw = existing;
      this.officialNumbers = existing.numbers
        ? [...existing.numbers]
        : [null, null, null, null, null, null];
      this.officialSpecialNumber = existing.specialNumber ?? null;
    } else {
      this.existingOfficialDraw = null;
    }
    this.cdr.markForCheck();
  }

  clearOfficialInputs() {
    this.officialNumbers = [null, null, null, null, null, null];
    this.officialSpecialNumber = null;
    this.saveOfficialMessage = '';
    this.cdr.markForCheck();
  }

  // LƯU KẾT QUẢ VIETLOTT THEO NGÀY VÀO DATABASE
  saveOfficialVietlottResult() {
    const maxLimit = this.category === 'POWER' ? 55 : 45;

    // Kiểm tra xem đã điền đủ 6 số chưa
    const filledNums = this.officialNumbers.map((n) => Number(n));
    for (let i = 0; i < 6; i++) {
      const val = filledNums[i];
      if (!val || isNaN(val) || val < 1 || val > maxLimit) {
        alert(`Vui lòng nhập đầy đủ 6 số chính từ 1 đến ${maxLimit} cho ô số ${i + 1}!`);
        return;
      }
    }

    // Kiểm tra trùng nhau giữa 6 số chính
    const uniqueSet = new Set(filledNums);
    if (uniqueSet.size !== 6) {
      alert('Các con số trong kết quả mở thưởng không được trùng nhau!');
      return;
    }

    // Nếu là Power 6/55, kiểm tra số phụ nếu có
    let specialNum: number | null = null;
    if (this.category === 'POWER') {
      if (
        this.officialSpecialNumber !== null &&
        this.officialSpecialNumber !== undefined &&
        this.officialSpecialNumber !== ('' as any)
      ) {
        specialNum = Number(this.officialSpecialNumber);
        if (isNaN(specialNum) || specialNum < 1 || specialNum > 55) {
          alert('Banh phụ của Power 6/55 phải là số từ 1 đến 55!');
          return;
        }
        if (uniqueSet.has(specialNum)) {
          alert(`Banh phụ (${specialNum}) không được trùng với bất kỳ số nào trong 6 số chính!`);
          return;
        }
      }
    }

    const payload = {
      category: this.category,
      drawDate: this.drawDate,
      numbers: filledNums.sort((a, b) => a - b),
      specialNumber: specialNum,
    };

    this.isSavingOfficial = true;
    this.saveOfficialMessage = '';
    this.cdr.markForCheck();

    if (this.editingOfficialDrawId) {
      this.analyzeService
        .editOfficialResult(this.editingOfficialDrawId, payload)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.isSavingOfficial = false;
            this.saveOfficialMessage = `✅ Đã cập nhật thành công kết quả Vietlott ${this.category === 'POWER' ? 'Power 6/55' : 'Mega 6/45'} ngày ${this.drawDate} vào Database!`;
            alert(`Thành công: Đã cập nhật kết quả Vietlott ngày ${this.drawDate} vào Database.`);
            this.editingOfficialDrawId = null;
            this.loadHistory();
            this.cdr.markForCheck();
          },
          error: (_err) => {
            this.isSavingOfficial = false;
            alert('Cập nhật kết quả thất bại! Vui lòng kiểm tra lại kết nối máy chủ.');
            this.cdr.markForCheck();
          },
        });
    } else {
      this.analyzeService
        .addOfficialResult(payload)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.isSavingOfficial = false;
            this.saveOfficialMessage = `✅ Đã lưu thành công kết quả Vietlott ${this.category === 'POWER' ? 'Power 6/55' : 'Mega 6/45'} ngày ${this.drawDate} vào Database!`;
            alert(`Thành công: Đã lưu kết quả Vietlott ngày ${this.drawDate} vào Database.`);
            this.loadHistory();
            this.cdr.markForCheck();
          },
          error: (_err) => {
            this.isSavingOfficial = false;
            alert('Lưu kết quả thất bại! Vui lòng kiểm tra lại kết nối máy chủ.');
            this.cdr.markForCheck();
          },
        });
    }
  }

  loadUserHistory() {
    this.analyzeService
      .getUserHistory()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.userCheckHistory = Array.isArray(res) ? res : [];
          this.cdr.markForCheck();
        },
        error: (_err) => {
          console.error('Lỗi tải lịch sử cá nhân từ DB:', _err);
          this.cdr.markForCheck();
        },
      });
  }

  addTicketRow() {
    this.userTickets.push(Array<number | null>(6).fill(null));
    this.cdr.markForCheck();
  }

  removeTicketRow(index: number) {
    if (this.userTickets.length > 1) {
      this.userTickets.splice(index, 1);
      this.cdr.markForCheck();
    }
  }

  submitCheck() {
    this.isChecking = true;
    this.checkResult = null;
    this.cdr.markForCheck();
    const payload = {
      category: this.category,
      drawDate: this.drawDate,
      tickets: this.userTickets.map((ticket) => ticket.map((n) => Number(n) || 0)),
    };

    this.analyzeService
      .checkTickets(payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.checkResult = res;
          this.isChecking = false;
          if (res.status === 'SUCCESS') {
            this.loadUserHistory();
          }
          this.cdr.markForCheck();
        },
        error: (_err) => {
          alert('Lỗi kết nối Server.');
          this.isChecking = false;
          this.cdr.markForCheck();
        },
      });
  }

  clearUserHistory() {
    if (confirm('Bạn có chắc chắn muốn xóa toàn bộ lịch sử dò vé cá nhân trong Database?')) {
      this.analyzeService
        .clearUserHistory()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.userCheckHistory = [];
            this.cdr.markForCheck();
          },
          error: (_err) => {
            console.error(_err);
            this.cdr.markForCheck();
          },
        });
    }
  }

  startEdit(draw: any) {
    this.editingDrawId = draw.id;
    this.editDrawDate = draw.drawDate;
    this.editNumbers = draw.numbers ? [...draw.numbers] : [];
    this.editSpecialNumber = draw.specialNumber ?? null;
    this.cdr.markForCheck();
  }

  cancelEdit() {
    this.editingDrawId = null;
    this.editDrawDate = '';
    this.editNumbers = [];
    this.editSpecialNumber = null;
    this.cdr.markForCheck();
  }

  saveEdit(draw: any) {
    if (!this.editDrawDate || !this.editDrawDate.trim()) {
      alert('Vui lòng chọn ngày mở thưởng!');
      return;
    }
    const maxLimit = this.category === 'POWER' ? 55 : 45;
    const filledNums = this.editNumbers.map((n) => Number(n));
    for (let i = 0; i < 6; i++) {
      const val = filledNums[i];
      if (!val || isNaN(val) || val < 1 || val > maxLimit) {
        alert(`Vui lòng nhập đầy đủ 6 số chính từ 1 đến ${maxLimit} cho ô số ${i + 1}!`);
        return;
      }
    }
    const uniqueSet = new Set(filledNums);
    if (uniqueSet.size !== 6) {
      alert('Các con số trong kết quả mở thưởng không được trùng nhau!');
      return;
    }

    let specialNum: number | null = null;
    if (this.category === 'POWER') {
      if (
        this.editSpecialNumber !== null &&
        this.editSpecialNumber !== undefined &&
        this.editSpecialNumber !== ('' as any)
      ) {
        specialNum = Number(this.editSpecialNumber);
        if (isNaN(specialNum) || specialNum < 1 || specialNum > 55) {
          alert('Banh phụ của Power 6/55 phải là số từ 1 đến 55!');
          return;
        }
        if (uniqueSet.has(specialNum)) {
          alert(`Banh phụ (${specialNum}) không được trùng với bất kỳ số nào trong 6 số chính!`);
          return;
        }
      }
    }

    const payload = {
      drawDate: this.editDrawDate.trim(),
      numbers: filledNums.sort((a, b) => a - b),
      specialNumber: this.category === 'POWER' ? specialNum : null,
    };

    this.analyzeService
      .editOfficialResult(draw.id, payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (msg) => {
          alert(msg || 'Đã chỉnh sửa ngày và dãy số thành công!');
          this.editingDrawId = null;
          this.editDrawDate = '';
          this.loadHistory();
          this.cdr.markForCheck();
        },
        error: (_err) => {
          alert('Có lỗi xảy ra khi cập nhật ngày và dãy số!');
          this.cdr.markForCheck();
        },
      });
  }
}
