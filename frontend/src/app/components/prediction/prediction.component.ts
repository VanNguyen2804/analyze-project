import { Component, OnInit } from '@angular/core';
import { PredictionService } from '../../services/prediction.service';

@Component({
  selector: 'app-prediction',
  templateUrl: './prediction.component.html',
  styleUrls: ['./prediction.component.css']
})
export class PredictionComponent implements OnInit {
  predictedNumbers: number[] = [];
  isSpinning: boolean = false;
  errorMessage: string | null = null;

  constructor(private predictionService: PredictionService) {}

  ngOnInit(): void {}

  onPredict(): void {
    this.isSpinning = true;
    this.errorMessage = null;

    this.predictionService.getPrediction().subscribe({
      next: (numbers) => {
        setTimeout(() => {
          this.predictedNumbers = numbers;
          this.isSpinning = false;
        }, 1000);
      },
      error: (err) => {
        console.error('Lỗi khi lấy dữ liệu dự đoán:', err);
        this.errorMessage = 'Không thể kết nối đến máy chủ. Sử dụng thuật toán dự phòng ngẫu nhiên.';
        setTimeout(() => {
          const fallbackSet = new Set<number>();
          while (fallbackSet.size < 6) {
            fallbackSet.add(Math.floor(Math.random() * 45) + 1);
          }
          this.predictedNumbers = Array.from(fallbackSet).sort((a, b) => a - b);
          this.isSpinning = false;
        }, 1000);
      }
    });
  }
}
