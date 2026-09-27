import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { AnalyzeService } from '../../core/services/analyze.service';

@Component({
  selector: 'app-latest-draw-analysis',
  templateUrl: './latest-draw-analysis.component.html',
  styleUrls: ['./latest-draw-analysis.component.css']
})
export class LatestDrawAnalysisComponent implements OnInit, OnDestroy {
  category: string = 'MEGA';
  selectedDate: string = '';
  algorithm: string = 'XGBoost';
  
  availableAlgorithms = [
    { id: 'XGBoost', name: 'AI XGBoost + Poisson' },
    { id: 'Markov', name: 'Mô hình chuỗi Markov' },
    { id: 'Frequency', name: 'Thống kê tần suất' }
  ];

  analysisData: any = null;
  isLoading: boolean = false;
  errorMessage: string | null = null;
  
  private categorySub: Subscription | undefined;

  constructor(private analyzeService: AnalyzeService) {}

  ngOnInit(): void {
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      this.category = newCategory;
      this.fetchAnalysis(true);
    });
  }

  ngOnDestroy(): void {
    if (this.categorySub) this.categorySub.unsubscribe();
  }

  changeCategory(cat: string) {
    this.analyzeService.setCategory(cat);
  }

  fetchAnalysis(useLatest: boolean = false): void {
    this.isLoading = true;
    this.errorMessage = null;

    if (useLatest) {
      this.selectedDate = ''; // Lấy ngày mới nhất từ DB
    }

    // Gửi đúng 3 tham số
    this.analyzeService.getOfficialDrawAnalysis(this.category, this.selectedDate, this.algorithm).subscribe({
      next: (response) => {
        this.analysisData = response;
        this.selectedDate = response.drawDate; // Map lại ngày trả về lên UI
        this.isLoading = false;
      },
      error: (error) => {
        this.errorMessage = 'Không tìm thấy dữ liệu phân tích cho ngày/loại vé này.';
        this.isLoading = false;
        this.analysisData = null;
      }
    });
  }
}