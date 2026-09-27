import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { AnalyzeService } from '../../core/services/analyze.service';

@Component({
  selector: 'app-latest-draw-analysis',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
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

  constructor(
    private analyzeService: AnalyzeService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.categorySub = this.analyzeService.currentCategory$.subscribe(newCategory => {
      if (newCategory) {
        this.category = newCategory;
        this.fetchAnalysis(true);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.categorySub) this.categorySub.unsubscribe();
  }

  changeCategory(cat: string) {
    this.category = cat;
    this.analyzeService.setCategory(cat);
    this.fetchAnalysis(true);
    this.cdr.markForCheck();
  }

  fetchAnalysis(useLatest: boolean = false): void {
    this.isLoading = true;
    this.errorMessage = null;

    if (useLatest) {
      this.selectedDate = ''; // Lấy ngày mới nhất từ DB
    }
    this.cdr.markForCheck();

    // Gửi đúng 3 tham số
    this.analyzeService.getOfficialDrawAnalysis(this.category, this.selectedDate, this.algorithm).subscribe({
      next: (response) => {
        this.analysisData = response;
        if (response && response.drawDate) {
          this.selectedDate = response.drawDate; // Map lại ngày trả về lên UI
        }
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.errorMessage = 'Không tìm thấy dữ liệu phân tích cho ngày/loại vé này.';
        this.isLoading = false;
        this.analysisData = null;
        this.cdr.markForCheck();
      }
    });
  }
}