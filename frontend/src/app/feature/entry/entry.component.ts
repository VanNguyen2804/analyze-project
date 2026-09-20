import { Component } from '@angular/core';
import { AnalyzeService } from '../../core/services/analyze.service';

@Component({
  selector: 'app-entry',
  templateUrl: './entry.component.html'
})
export class EntryComponent {
  category: string = 'MEGA';
  drawDate: string = new Date().toISOString().split('T')[0];
  
  // Quản lý danh sách vé người dùng nhập
  userTickets: number[][] = [
    [0, 0, 0, 0, 0, 0] // Khởi tạo 1 vé trống
  ];

  checkResult: any = null;
  isChecking = false;
  isUpdating = false;

  constructor(private analyzeService: AnalyzeService) {}

  addTicketRow() {
    this.userTickets.push([0, 0, 0, 0, 0, 0]);
  }

  removeTicketRow(index: number) {
    if (this.userTickets.length > 1) {
      this.userTickets.splice(index, 1);
    }
  }

  // TrackBy dùng cho ngFor với mảng nguyên thủy (number)
  trackByIndex(index: number, obj: any): any {
    return index;
  }

  submitCheck() {
    this.isChecking = true;
    this.checkResult = null;

    const payload = {
      category: this.category,
      drawDate: this.drawDate,
      tickets: this.userTickets.map(ticket => ticket.map(n => Number(n)))
    };

    this.analyzeService.checkTickets(payload).subscribe({
      next: (res) => {
        this.checkResult = res;
        this.isChecking = false;
      },
      error: (err) => {
        alert('Có lỗi xảy ra kết nối Server');
        this.isChecking = false;
      }
    });
  }

  // Tính năng nhập tay kết quả chính thức để cập nhật thuật toán
  updateOfficialResult() {
    // Lấy vé đầu tiên làm kết quả chính thức (mô phỏng)
    const officialNums = this.userTickets[0].map(n => Number(n));
    const payload = {
      category: this.category,
      drawDate: this.drawDate,
      numbers: officialNums,
      specialNumber: this.category === 'POWER' ? officialNums[5] : null // Xử lý logic số phụ tùy giao diện
    };

    this.isUpdating = true;
    this.analyzeService.addOfficialResult(payload).subscribe({
      next: (msg) => {
        alert(msg); // Hiển thị thông báo thành công
        this.isUpdating = false;
      },
      error: () => {
        this.isUpdating = false;
      }
    });
  }
}