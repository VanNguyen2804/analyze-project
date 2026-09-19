import { Component, OnInit } from '@angular/core';
import { PredictionService } from '../../core/services/prediction.service';

@Component({
  selector: 'app-prediction',
  templateUrl: './prediction.component.html',
  styleUrls: ['./prediction.component.css']
})
export class PredictionComponent implements OnInit {
  category: 'MEGA' | 'POWER' = 'MEGA';
  predictedNumbers: number[] = [];
  isSpinning: boolean = false;
  errorMessage: string | null = null;

  constructor(private predictionService: PredictionService) {}

  ngOnInit(): void {}

  setCategory(cat: 'MEGA' | 'POWER'): void {
    this.category = cat;
    this.predictedNumbers = [];
    this.errorMessage = null;
  }

  onPredict(): void {
    this.isSpinning = true;
    this.errorMessage = null;

    this.predictionService.getPrediction(this.category).subscribe({
      next: (numbers) => {
        setTimeout(() => {
          this.predictedNumbers = numbers;
          this.isSpinning = false;
        }, 600);
      },
      error: (err) => {
        console.error('Lỗi khi lấy dữ liệu dự đoán:', err);
        this.errorMessage = 'Không thể kết nối đến máy chủ. Sử dụng thuật toán dự phòng.';
        setTimeout(() => {
          const max = this.category === 'POWER' ? 55 : 45;
          const fallbackSet = new Set<number>();
          while (fallbackSet.size < 6) {
            fallbackSet.add(Math.floor(Math.random() * max) + 1);
          }
          this.predictedNumbers = Array.from(fallbackSet).sort((a, b) => a - b);
          this.isSpinning = false;
        }, 600);
      }
    });
  }
}
