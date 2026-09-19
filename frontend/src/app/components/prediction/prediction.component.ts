import { Component } from '@angular/core';
import { AnalyzeService } from '../services/analyze.service';

@Component({
  selector: 'app-prediction',
  templateUrl: './prediction.component.html',
  styleUrls: ['./prediction.component.css']
})
export class PredictionComponent {
  predictedNumbers: number[] = [];
  isSpinning = false;

  constructor(private analyzeService: AnalyzeService) {}

  predict() {
    this.isSpinning = true;
    this.analyzeService.getPrediction().subscribe(nums => {
      setTimeout(() => {
        this.predictedNumbers = nums;
        this.isSpinning = false;
      }, 1000); 
    });
  }
}
