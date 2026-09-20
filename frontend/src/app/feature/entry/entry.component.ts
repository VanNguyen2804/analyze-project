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
  
  userTickets: number[][] = [ [null as any, null as any, null as any, null as any, null as any, null as any] ];
  officialSpecialNumber: number | null = null;

  checkResult: any = null;
  isChecking = false;
  isUpdating = false;

  recentDraws: any[] = [];
  userCheckHistory: any[] = [];

  editingDrawId: number | null = null;
  editNumbers: number[] = [];
  editSpecialNumber: number | null = null;

  // Khai báo biến lưu trữ Subscription để hủy khi rời trang
  private categorySub: Subscription | undefined;

  constructor(private analyzeService: AnalyzeService) {}

  ngOnInit() {
    // Lắng nghe sự thay đổi Category từ Header
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      this.category = newCategory;
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
    this.analyzeService.setCategory(newCategory);
  }

  loadHistory() {
    this.analyzeService.getHistory(this.category).subscribe({
      next: (res) => this.recentDraws = res,
      error: (err) => console.error('Lỗi tải lịch sử database:', err)
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

  updateOfficialResult() {
    const officialNums = this.userTickets[0].map(n => Number(n) || 0);
    const payload = {
      category: this.category,
      drawDate: this.drawDate,
      numbers: officialNums,
      specialNumber: this.category === 'POWER' ? Number(this.officialSpecialNumber || 0) : null
    };

    this.isUpdating = true;
    this.analyzeService.addOfficialResult(payload).subscribe({
      next: (msg) => {
        alert('Đã nạp kết quả vào hệ thống: ' + msg);
        this.isUpdating = false;
        this.submitCheck(); 
        this.loadHistory();
      },
      error: (err) => {
        alert('Lưu thất bại! Kiểm tra log Spring Boot.');
        this.isUpdating = false;
      }
    });
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