import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AnalyzeService } from 'src/app/core/services/analyze.service';

@Component({
  selector: 'app-entry',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './entry.component.html'
})
export class EntryComponent implements OnInit, OnDestroy {
  category: string = 'MEGA';
  drawDate: string = new Date().toISOString().split('T')[0];
  
  // Dãy số kết quả Vietlott chính thức theo ngày
  officialNumbers: (number | null)[] = [null, null, null, null, null, null];
  officialSpecialNumber: number | null = null;
  isSavingOfficial: boolean = false;
  saveOfficialMessage: string = '';
  existingOfficialDraw: any = null;

  // Dãy số vé cá nhân để dò
  userTickets: number[][] = [ [null as any, null as any, null as any, null as any, null as any, null as any] ];

  checkResult: any = null;
  isChecking = false;

  recentDraws: any[] = [];
  userCheckHistory: any[] = [];
  isLoadingHistory: boolean = false;

  editingDrawId: number | null = null;
  editNumbers: number[] = [];
  editSpecialNumber: number | null = null;

  // Khai báo biến lưu trữ Subscription để hủy khi rời trang
  private categorySub: Subscription | undefined;

  constructor(private analyzeService: AnalyzeService) {}

  ngOnInit() {
    // Lắng nghe sự thay đổi Category từ Header
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      if (newCategory) {
        this.category = newCategory;
      }
      this.loadHistory(); // Tự động tải lại bảng lịch sử Database
    });

    this.loadUserHistory();
  }

  ngOnDestroy() {
    // Tránh rò rỉ bộ nhớ khi chuyển sang trang khác
    if (this.categorySub) {
      this.categorySub.unsubscribe();
    }
  }

  // Hàm đồng bộ ngược lên Header khi đổi Category tại Dropdown của trang này
  onCategoryChange(newCategory: string) {
    if (newCategory) {
      this.category = newCategory;
      this.analyzeService.setCategory(newCategory);
      this.loadHistory();
    }
  }

  onDateOrCategoryChange() {
    this.checkExistingOfficialDraw();
  }

  loadHistory() {
    this.isLoadingHistory = true;
    this.analyzeService.getHistory(this.category).subscribe({
      next: (res) => {
        this.isLoadingHistory = false;
        this.recentDraws = Array.isArray(res) ? res : [];
        this.checkExistingOfficialDraw();
      },
      error: (err) => {
        this.isLoadingHistory = false;
        console.error('Lỗi tải lịch sử database:', err);
        this.recentDraws = [];
      }
    });
  }

  checkExistingOfficialDraw() {
    if (!this.recentDraws || !Array.isArray(this.recentDraws)) {
      this.existingOfficialDraw = null;
      return;
    }
    const existing = this.recentDraws.find(
      (d: any) => d.drawDate === this.drawDate
    );
    if (existing) {
      this.existingOfficialDraw = existing;
      this.officialNumbers = existing.numbers ? [...existing.numbers] : [null, null, null, null, null, null];
      this.officialSpecialNumber = existing.specialNumber ?? null;
    } else {
      this.existingOfficialDraw = null;
    }
  }

  clearOfficialInputs() {
    this.officialNumbers = [null, null, null, null, null, null];
    this.officialSpecialNumber = null;
    this.saveOfficialMessage = '';
  }

  // LƯU KẾT QUẢ VIETLOTT THEO NGÀY VÀO DATABASE
  saveOfficialVietlottResult() {
    const maxLimit = this.category === 'POWER' ? 55 : 45;

    // Kiểm tra xem đã điền đủ 6 số chưa
    const filledNums = this.officialNumbers.map(n => Number(n));
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
      if (this.officialSpecialNumber !== null && this.officialSpecialNumber !== undefined && this.officialSpecialNumber !== ('' as any)) {
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
      specialNumber: specialNum
    };

    this.isSavingOfficial = true;
    this.saveOfficialMessage = '';

    this.analyzeService.addOfficialResult(payload).subscribe({
      next: (msg) => {
        this.isSavingOfficial = false;
        this.saveOfficialMessage = `✅ Đã lưu thành công kết quả Vietlott ${this.category === 'POWER' ? 'Power 6/55' : 'Mega 6/45'} ngày ${this.drawDate} vào Database!`;
        alert(`Thành công: Đã lưu kết quả Vietlott ngày ${this.drawDate} vào Database.`);
        this.loadHistory();
      },
      error: (err) => {
        this.isSavingOfficial = false;
        alert('Lưu kết quả thất bại! Vui lòng kiểm tra lại kết nối máy chủ.');
      }
    });
  }

  loadUserHistory() {
    this.analyzeService.getUserHistory().subscribe({
      next: (res) => this.userCheckHistory = res,
      error: (err) => console.error('Lỗi tải lịch sử cá nhân từ DB:', err)
    });
  }

  addTicketRow() {
    this.userTickets.push([null as any, null as any, null as any, null as any, null as any, null as any]);
  }

  removeTicketRow(index: number) {
    if (this.userTickets.length > 1) {
      this.userTickets.splice(index, 1);
    }
  }

  trackByIndex(index: number, obj: any): any {
    return index;
  }

  submitCheck() {
    this.isChecking = true;
    this.checkResult = null;
    const payload = {
      category: this.category,
      drawDate: this.drawDate,
      tickets: this.userTickets.map(ticket => ticket.map(n => Number(n) || 0))
    };

    this.analyzeService.checkTickets(payload).subscribe({
      next: (res) => {
        this.checkResult = res;
        this.isChecking = false;
        if (res.status === 'SUCCESS') {
          this.loadUserHistory();
        }
      },
      error: (err) => {
        alert('Lỗi kết nối Server.');
        this.isChecking = false;
      }
    });
  }

  clearUserHistory() {
    if(confirm('Bạn có chắc chắn muốn xóa toàn bộ lịch sử dò vé cá nhân trong Database?')) {
      this.analyzeService.clearUserHistory().subscribe({
        next: () => this.userCheckHistory = [],
        error: (err) => console.error(err)
      });
    }
  }

  startEdit(draw: any) {
    this.editingDrawId = draw.id;
    this.editNumbers = [...draw.numbers]; 
    this.editSpecialNumber = draw.specialNumber;
  }

  cancelEdit() {
    this.editingDrawId = null;
    this.editNumbers = [];
    this.editSpecialNumber = null;
  }

  saveEdit(draw: any) {
    const payload = {
      numbers: this.editNumbers.map(n => Number(n) || 0),
      specialNumber: this.category === 'POWER' ? Number(this.editSpecialNumber || 0) : null
    };

    this.analyzeService.editOfficialResult(draw.id, payload).subscribe({
      next: (msg) => {
        alert(msg);
        this.editingDrawId = null;
        this.loadHistory(); 
      },
      error: (err) => {
        alert('Có lỗi xảy ra khi cập nhật số!');
      }
    });
  }
}