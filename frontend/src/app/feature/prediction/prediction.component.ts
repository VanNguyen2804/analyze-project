import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { PredictionService, PredictionResponse, NumberScoreDetail } from '../../core/services/prediction.service';
import { LotteryService } from '../../core/services/lottery.service';
import { CategoryService } from '../../core/services/category.service';

@Component({
  selector: 'app-prediction',
  templateUrl: './prediction.component.html',
  styleUrls: ['./prediction.component.css']
})
export class PredictionComponent implements OnInit, OnDestroy {
  category: 'MEGA' | 'POWER' = 'POWER';
  predictionResult: PredictionResponse | null = null;
  predictedNumbers: number[] = [];
  isSpinning: boolean = false;
  isSaving: boolean = false;
  saveMessage: string | null = null;
  errorMessage: string | null = null;
  private catSub?: Subscription;

  constructor(
    private predictionService: PredictionService,
    private lotteryService: LotteryService,
    private categoryService: CategoryService
  ) {}

  ngOnInit(): void {
    this.category = this.categoryService.currentCategory;
    this.catSub = this.categoryService.category$.subscribe(cat => {
      if (this.category !== cat || this.predictedNumbers.length === 0) {
        this.category = cat;
        this.onPredict();
      }
    });
  }

  ngOnDestroy(): void {
    this.catSub?.unsubscribe();
  }

  setCategory(cat: 'MEGA' | 'POWER'): void {
    this.categoryService.setCategory(cat);
  }

  onPredict(): void {
    this.isSpinning = true;
    this.errorMessage = null;
    this.saveMessage = null;

    this.predictionService.getPrediction(this.category).subscribe({
      next: (result: any) => {
        setTimeout(() => {
          if (result && Array.isArray(result.numbers)) {
            this.predictionResult = result as PredictionResponse;
            this.predictedNumbers = result.numbers;
          } else if (Array.isArray(result)) {
            this.predictedNumbers = result;
            this.predictionResult = {
              category: this.category,
              numbers: result,
              totalDrawsAnalyzed: 0,
              hotNumbers: [],
              coldNumbers: [],
              frequentPairs: [],
              oddEvenRatio: '3 Chẵn / 3 Lẻ',
              analysisSummary: `Đề xuất bộ số cho ${this.category === 'POWER' ? 'Power 6/55' : 'Mega 6/45'}`,
              details: result.map((n: number) => ({
                number: n,
                probabilityPercent: 75.0,
                frequency: 1,
                drawGap: 2,
                tag: 'CÂN BẰNG'
              }))
            };
          }
          this.isSpinning = false;
        }, 400);
      },
      error: (err: any) => {
        console.error('Lỗi khi lấy dữ liệu dự đoán:', err);
        this.errorMessage = 'Không thể kết nối đến máy chủ. Sử dụng thuật toán dự phòng.';
        setTimeout(() => {
          const max = this.category === 'POWER' ? 55 : 45;
          const fallbackSet = new Set<number>();
          while (fallbackSet.size < 6) {
            fallbackSet.add(Math.floor(Math.random() * max) + 1);
          }
          const sorted = Array.from(fallbackSet).sort((a, b) => a - b);
          this.predictedNumbers = sorted;
          this.predictionResult = {
            category: this.category,
            numbers: sorted,
            totalDrawsAnalyzed: 0,
            hotNumbers: [],
            coldNumbers: [],
            frequentPairs: [],
            oddEvenRatio: '3 Chẵn / 3 Lẻ',
            analysisSummary: `Đề xuất dự phòng cho ${this.category === 'POWER' ? 'Power 6/55' : 'Mega 6/45'}`,
            details: sorted.map((n) => ({
              number: n,
              probabilityPercent: 65.0,
              frequency: 0,
              drawGap: 0,
              tag: 'CÂN BẰNG'
            }))
          };
          this.isSpinning = false;
        }, 400);
      }
    });
  }

  savePredictedNumbers(): void {
    if (this.predictedNumbers.length !== 6) return;

    this.isSaving = true;
    this.saveMessage = null;

    const todayStr = new Date().toISOString().slice(0, 10);
    const catName = this.category === 'POWER' ? 'Power 6/55' : 'Mega 6/45';

    this.lotteryService.saveNumbers({
      numbers: this.predictedNumbers,
      category: this.category,
      drawDate: todayStr,
      note: `Dự đoán AI XGBoost (${catName} - Ngày ${todayStr})`
    }).subscribe({
      next: (saved: any) => {
        this.isSaving = false;
        this.saveMessage = `Đã lưu thành công bộ số dự đoán ${saved.category} (#${saved.id}) vào hệ thống!`;
      },
      error: (err: any) => {
        console.error('Lỗi khi lưu bộ số dự đoán:', err);
        this.isSaving = false;
        this.saveMessage = 'Không thể lưu bộ số lúc này.';
      }
    });
  }

  formatNumber(num: number): string {
    return num < 10 ? '0' + num : '' + num;
  }
}
