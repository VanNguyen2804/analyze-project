import { Component, OnInit } from '@angular/core';
import { AnalyzeService } from 'src/app/core/services/analyze.service';

@Component({
  selector: 'app-entry',
  templateUrl: './entry.component.html'
})
export class EntryComponent implements OnInit {
  category: string = 'MEGA';
  drawDate: string = new Date().toISOString().split('T')[0];
  
  userTickets: number[][] = [ [null as any, null as any, null as any, null as any, null as any, null as any] ];
  officialSpecialNumber: number | null = null;

  checkResult: any = null;
  isChecking = false;
  isUpdating = false;

  recentDraws: any[] = [];
  
  // Biến lưu trữ lịch sử dò vé của cá nhân
  userCheckHistory: any[] = [];

  constructor(private analyzeService: AnalyzeService) {}

  ngOnInit() {
    this.loadHistory();
    this.loadUserHistory();
    // Tải lịch sử dò vé của người dùng từ LocalStorage khi khởi động trang
  }

  loadUserHistory() {
    this.analyzeService.getUserHistory().subscribe({
      next: (res) => this.userCheckHistory = res,
      error: (err) => console.error('Lỗi tải lịch sử cá nhân từ DB:', err)
    });
  }

  loadHistory() {
    this.analyzeService.getHistory(this.category).subscribe({
      next: (res) => this.recentDraws = res,
      error: (err) => console.error('Lỗi tải lịch sử database:', err)
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
          this.loadUserHistory(); // Cập nhật lại bảng lịch sử sau khi dò xong
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

  // Nạp kết quả vào Database cho AI phân tích
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
}