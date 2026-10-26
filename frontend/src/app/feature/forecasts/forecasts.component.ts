import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-forecasts', standalone: true, imports: [CommonModule, FormsModule],
  template: `
    <section class="container-fluid py-4">
      <h2>Dự đoán đã lưu và đối soát</h2>
      <p>Mỗi thuật toán giữ nguyên bộ 6 số đầu tiên đã lưu cho mỗi kỳ trước 18:00 giờ Việt Nam.
        Kết quả được đối soát từ 18:30 khi có dữ liệu hợp lệ. Báo cáo chỉ đo các dự đoán đã lưu trước kỳ quay.</p>
      <div class="d-flex gap-2 align-items-center mb-3 flex-wrap">
        <label for="forecast-category">Loại xổ số</label>
        <select id="forecast-category" class="form-select w-auto" [(ngModel)]="category" (change)="load()">
          <option value="MEGA">MEGA 6/45</option><option value="POWER">POWER 6/55</option>
        </select>
        <button class="btn btn-outline-primary" (click)="load()" [disabled]="busy">Tải lại và đối soát</button>
        <button class="btn btn-primary" (click)="capture()" [disabled]="busy">Lưu dự đoán kỳ tiếp theo</button>
      </div>
      <p class="alert alert-danger" *ngIf="error" role="alert">{{ error }}</p>
      <p *ngIf="busy" role="status">Đang xử lý…</p>
      <p class="alert alert-warning" *ngIf="ledger && !ledger.enabled">Tự động lưu dự đoán đang tắt.</p>
      <p class="alert alert-info" *ngIf="message" role="status">{{ message }}</p>
      <h3 class="h5">Đánh giá theo phiên bản và cấu hình</h3>
      <p>Đối chứng được lưu cùng thời điểm với dự đoán. Mẫu nhỏ và chênh lệch trung bình chưa chứng minh khả năng dự đoán tốt hơn.</p>
      <div class="table-responsive">
        <table class="table table-striped">
          <thead><tr><th>Thuật toán / phiên bản</th><th>Cấu hình</th><th>Đã đối soát / đã lưu</th><th>Khớp TB</th><th>Tần suất nền</th><th>Ngẫu nhiên</th><th>Khớp ≥3</th></tr></thead>
          <tbody><tr *ngFor="let group of groups">
            <td>{{ group.algorithm }}<br><small>{{ group.modelVersion }}</small></td>
            <td><small>{{ group.configurationHash.slice(0, 10) }}</small></td>
            <td>{{ group.evaluatedDraws }} / {{ group.savedForecasts }}</td>
            <td>{{ metric(group.averageMatched) }}</td><td>{{ metric(group.frequencyAverageMatched) }}</td>
            <td>{{ metric(group.randomAverageMatched) }}</td><td>{{ metric(group.hit3RatePercent) }}{{ group.hit3RatePercent == null ? '' : '%' }}</td>
          </tr></tbody>
        </table>
      </div>
      <p *ngIf="!busy && !groups.length">Chưa có dự đoán đã lưu cho loại xổ số này. Hệ thống không tạo dự đoán hồi tố cho các kỳ đã quay.</p>
      <h3 class="h5 mt-4">Các bộ số đã chốt</h3>
      <div class="table-responsive"><table class="table">
        <thead><tr><th>Kỳ đích</th><th>Thuật toán</th><th>Bộ 6 số</th><th>Thời điểm lưu (Việt Nam)</th><th>Kết quả</th><th>Số khớp</th></tr></thead>
        <tbody><tr *ngFor="let row of rows">
          <td>{{ row.targetDate }}</td><td>{{ row.algorithm }}</td><td>{{ row.numbers.join(' – ') }}</td>
          <td>{{ row.createdAt | date:'dd/MM/yyyy HH:mm:ss':'+0700' }}</td>
          <td>{{ status(row.settlement?.status) }}</td><td>{{ row.settlement?.matched ?? 'N/A' }}</td>
        </tr></tbody>
      </table></div>
      <details *ngIf="ledger?.lastRun"><summary>Chi tiết lần lưu tự động gần nhất</summary><pre class="mt-2">{{ ledger.lastRun | json }}</pre></details>
    </section>`
})
export class ForecastsComponent implements OnInit {
  category = 'MEGA'; busy = false; error = ''; message = ''; ledger: any; groups: any[] = []; rows: any[] = [];
  private url = `${environment.apiUrl || ''}/api/forecasts`;
  constructor(private http: HttpClient, private change: ChangeDetectorRef) {}
  ngOnInit() { this.load(); }
  metric(value: number | null | undefined) { return value == null ? 'N/A' : value.toFixed(3); }
  status(value?: string) { return value === 'SETTLED' ? 'Đã đối soát' : value === 'RESULT_UNAVAILABLE' ? 'Kết quả cần xác minh lại' : 'Chờ kết quả hợp lệ'; }
  load() {
    this.busy = true; this.error = '';
    forkJoin({ ledger: this.http.get<any>(this.url), evaluation: this.http.get<any>(`${this.url}/evaluation?category=${this.category}`) }).subscribe({
      next: ({ ledger, evaluation }) => {
        this.ledger = ledger; this.groups = evaluation.groups;
        const latest = new Map<string, any>();
        ledger.settlements.forEach((s: any) => latest.set(s.forecastId,s));
        this.rows = ledger.forecasts.filter((f: any) => f.category === this.category)
          .map((f: any) => ({ ...f, settlement: latest.get(f.id) })).sort((a: any,b: any) => b.targetDate.localeCompare(a.targetDate));
        this.busy = false; this.change.markForCheck();
      },
      error: () => { this.error = 'Không tải được dữ liệu dự đoán. Kiểm tra kết nối và backend.'; this.busy = false; this.change.markForCheck(); }
    });
  }
  capture() {
    this.busy = true; this.error = ''; this.message = '';
    this.http.post<any>(`${this.url}/capture`,{}).subscribe({
      next: () => { this.message = 'Đã kiểm tra lưu dự đoán. Xem các bộ số đã chốt và chi tiết lần lưu để biết trạng thái từng thuật toán.'; this.load(); },
      error: () => { this.error = 'Không hoàn tất lưu dự đoán. Bản đã lưu trước đó vẫn được giữ nguyên.'; this.busy = false; this.change.markForCheck(); }
    });
  }
}
