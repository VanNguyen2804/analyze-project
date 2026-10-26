import { Component, OnInit, ChangeDetectorRef, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { EMPTY, Subject, switchMap, catchError, finalize, tap, distinctUntilChanged } from 'rxjs';
import { RouterLink } from '@angular/router';
import { AnalyzeService } from '../../core/services/analyze.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-forecasts', standalone: true, imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './forecasts.component.html',
  styleUrls: ['./forecasts.component.css']
})
export class ForecastsComponent implements OnInit {
  category = 'MEGA'; busy = false; error = ''; message = ''; ledger: any; groups: any[] = []; rows: any[] = [];
  page = 0; totalRows = 0;
  mutating = false;
  statusFilter = 'ALL';
  private loadedCategory = '';
  private readonly destroyRef = inject(DestroyRef);
  private readonly requests = new Subject<{ category: string; page: number }>();
  private url = `${environment.apiUrl || ''}/api/forecasts`;
  constructor(private http: HttpClient, private change: ChangeDetectorRef, private analyze: AnalyzeService) {}
  ngOnInit() {
    this.requests.pipe(switchMap(query => {
      this.busy = true; this.error = '';
      return this.http.get<any>(`${this.url}/overview?category=${query.category}&page=${query.page}&size=50`).pipe(
        tap(payload => {
          this.ledger = payload; this.groups = payload.evaluation.groups;
          this.rows = payload.rows; this.totalRows = payload.totalRows;
          this.loadedCategory = query.category;
        }),
        catchError(error => {
          this.error = error.status === 503 ? 'Máy chủ đang quá tải. Vui lòng chờ rồi tải lại.' : 'Không tải được dữ liệu dự đoán. Kiểm tra kết nối và backend.';
          return EMPTY;
        }),
        finalize(() => { this.busy = false; this.change.markForCheck(); })
      );
    }), takeUntilDestroyed(this.destroyRef)).subscribe();
    this.analyze.currentCategory$.pipe(distinctUntilChanged(),takeUntilDestroyed(this.destroyRef)).subscribe(category => {
      this.category = category === 'POWER' ? 'POWER' : 'MEGA';
      this.statusFilter = 'ALL';
      if (this.category !== this.loadedCategory) { this.rows = []; this.groups = []; this.totalRows = 0; this.ledger = undefined; }
      this.load();
    });
  }
  categoryChanged() { this.analyze.setCategory(this.category); }
  get evaluatedCount(): number { return this.groups.reduce((sum,g) => sum + (g.evaluatedDraws ?? 0),0); }
  get pendingCount(): number { return this.groups.reduce((sum,g) => sum + (g.pendingOrUnavailable ?? 0),0); }
  get hit3Count(): number { return this.groups.reduce((sum,g) => sum + (g.matchHistogram ?? []).slice(3).reduce((a: number,b: number) => a+b,0),0); }
  get pageCount(): number { return Math.max(1,Math.ceil(this.totalRows/50)); }
  get filteredRows(): any[] { return this.statusFilter === 'ALL' ? this.rows : this.rows.filter(row => (row.settlement?.status ?? 'PENDING') === this.statusFilter); }
  trackTicket(_index: number, row: any) { return row.id; }
  algorithmName(id: string): string {
    return ({ temporal_learning: 'Học liên kết giữa các kỳ', bayesian_graph: 'Bayes & liên kết cặp số', deep_stacking: 'Tổ hợp tần suất & độ trễ' } as Record<string,string>)[id] ?? id;
  }
  isMatched(row: any, number: number): boolean { return row.settlement?.status === 'SETTLED' && (row.settlement.actualNumbers ?? []).includes(number); }
  metric(value: number | null | undefined) { return value == null ? 'N/A' : value.toFixed(3); }
  status(value?: string) { return value === 'SETTLED' ? 'Đã đối soát' : value === 'RESULT_UNAVAILABLE' ? 'Cần xác minh lại' : 'Chờ kết quả'; }
  load(resetPage = true) {
    if (this.mutating) return;
    if (this.category !== this.loadedCategory) { this.rows = []; this.groups = []; this.totalRows = 0; this.ledger = undefined; }
    if (resetPage) this.page = 0;
    this.requests.next({ category: this.category, page: this.page });
  }
  changePage(delta: number) { if (!this.busy) { this.page += delta; this.load(false); } }
  reconcile() { this.mutate('reconcile'); }
  capture() {
    this.mutate('capture');
  }
  private mutate(action: 'capture' | 'reconcile') {
    if (this.busy) return;
    this.mutating = true;
    this.busy = true; this.error = ''; this.message = '';
    this.http.post<any>(`${this.url}/${action}`,{}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        this.mutating = false;
        this.message = result.status === 'BUSY' ? result.message : action === 'capture'
          ? 'Đã kiểm tra lưu kỳ tiếp theo. Xem các bộ số đã chốt bên dưới; bản đầu tiên luôn được giữ nguyên.' : 'Đã đối soát kết quả hiện có.';
        this.load();
      },
      error: () => { this.mutating = false; this.error = 'Không hoàn tất thao tác. Vui lòng chờ rồi thử lại.'; this.busy = false; this.change.markForCheck(); }
    });
  }
}
