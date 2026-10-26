import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, distinctUntilChanged, finalize, switchMap, timer } from 'rxjs';
import { AnalyzeService } from '../../core/services/analyze.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-ai-lab', standalone: true, imports: [CommonModule],
  templateUrl: './ai-lab.component.html', styleUrls: ['./ai-lab.component.css']
})
export class AiLabComponent implements OnInit {
  category = 'MEGA'; page = 0; data: any; busy = false; mutating = false; error = ''; message = '';
  private readonly destroy = inject(DestroyRef);
  private readonly change = inject(ChangeDetectorRef);
  private readonly http = inject(HttpClient);
  private readonly analyze = inject(AnalyzeService);
  private readonly requests = new Subject<{category: string; page: number}>();
  private readonly url = `${environment.apiUrl || ''}/api/ai-lab`;
  ngOnInit() {
    this.requests.pipe(switchMap(query => {
      this.busy = true; this.error = '';
      return this.http.get<any>(`${this.url}?category=${query.category}&page=${query.page}`).pipe(
        catchError(() => { this.error = 'Không tải được phòng thử nghiệm. Hãy thử tải lại.'; return EMPTY; }),
        finalize(() => { this.busy = false; this.change.markForCheck(); }));
    }),takeUntilDestroyed(this.destroy)).subscribe(data => { this.data = data; this.change.markForCheck(); });
    this.analyze.currentCategory$.pipe(distinctUntilChanged(),takeUntilDestroyed(this.destroy)).subscribe(category => {
      this.category = category === 'POWER' ? 'POWER' : 'MEGA'; this.page = 0; this.data = undefined; this.load();
    });
    timer(60000,60000).pipe(takeUntilDestroyed(this.destroy)).subscribe(() => { if(!this.busy && !this.mutating) this.load(); });
  }
  load() { this.requests.next({category: this.category,page: this.page}); }
  select(category: string) { if(!this.mutating) this.analyze.setCategory(category); }
  changePage(delta: number) { if(!this.busy && !this.mutating) { this.page += delta; this.load(); } }
  run() {
    if(this.busy || this.mutating) return;
    this.mutating = true; this.error = ''; this.message = '';
    this.http.post<any>(`${this.url}/run`,{}).pipe(takeUntilDestroyed(this.destroy),finalize(() => { this.mutating = false; this.change.markForCheck(); })).subscribe({
      next: result => { this.message = result.status === 'BUSY' ? 'Tác vụ đang chạy, hãy chờ lượt cập nhật tiếp theo.' : 'Đã kiểm tra chốt kỳ tiếp theo và đối soát. Xem trạng thái lưu bên dưới.'; this.load(); },
      error: () => this.error = 'Không hoàn tất thao tác. Hãy thử lại sau.'
    });
  }
  name(model: string) { return model === 'logistic' ? 'Logistic Regression (học máy)' : 'Tần suất suy giảm theo thời gian'; }
  percent(value: number | null | undefined) { return value == null ? 'Chưa có' : `${value.toFixed(1)}%`; }
  matched(row: any, number: number) { return row.settlement?.status === 'SETTLED' && row.settlement.actualNumbers.includes(number); }
  get pages() { return Math.max(1,Math.ceil((this.data?.totalRows ?? 0)/50)); }
}
