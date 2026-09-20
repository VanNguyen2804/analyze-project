import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { PredictionPayload } from 'src/app/core/models/prediction-payload.model';
import { AnalyzeService } from 'src/app/core/services/analyze.service';

@Component({
  selector: 'app-prediction',
  templateUrl: './prediction.component.html',
  styleUrls: ['./prediction.component.css']
})
export class PredictionComponent implements OnInit, OnDestroy {
  payload: PredictionPayload | null = null;
  isSpinning = false;
  isLoadingHistory = false;
  showAllReasons = false;
  
  // Lưu trữ chi tiết con số khi bấm vào để truyền ra Popup Modal
  selectedNumberDetail: any = null;
  
  category: string = 'MEGA'; 
  private categorySub: Subscription | undefined;

  recentDraws: any[] = [];
  visibleDraws: any[] = [];

  constructor(private analyzeService: AnalyzeService) {}

  ngOnInit() {
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      this.category = newCategory;
      this.payload = null; 
      this.showAllReasons = false;
      this.selectedNumberDetail = null;
      this.recentDraws = [];
      this.visibleDraws = [];
    });
  }

  ngOnDestroy() {
    if (this.categorySub) {
      this.categorySub.unsubscribe();
    }
  }

  predict() {
    this.isSpinning = true;
    this.isLoadingHistory = true;
    this.payload = null;
    this.showAllReasons = false;
    this.selectedNumberDetail = null;
    
    this.analyzeService.getPrediction(this.category).subscribe({
      next: (res) => {
        setTimeout(() => {
          this.payload = res;
          
          if (res && res.recentDraws) {
            this.recentDraws = res.recentDraws;
            this.visibleDraws = this.recentDraws.slice(0, 10);
          }
          
          this.isSpinning = false;
          this.isLoadingHistory = false;
        }, 1500); 
      },
      error: (err) => {
        console.error('Lỗi khi phân tích dữ liệu:', err);
        this.isSpinning = false;
        this.isLoadingHistory = false;
      }
    });
  }

  // Hàm được gọi khi bấm vào 1 số trong vé để mở Modal
  openNumberDetail(num: number) {
    if (this.payload && this.payload.selectionReasons) {
      this.selectedNumberDetail = this.payload.selectionReasons.find(r => r.number === num);
    }
  }

  formatNumber(num: number | undefined): string {
    if (num === undefined || num === null) return '--';
    return num < 10 ? '0' + num : num.toString();
  }

  getDayOfWeek(dateString: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    const days = ['Chủ nhật', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];
    return days[date.getDay()];
  }

  showMoreHistory() {
    this.visibleDraws = [...this.recentDraws];
  }

  collapseHistory() {
    this.visibleDraws = this.recentDraws.slice(0, 10);
  }
}