import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { LotteryCategory } from '../models/lottery-category.model';
import { BenchmarkReport, EvidenceInventory } from '../models/prediction-research.model';

@Injectable({ providedIn: 'root' })
export class PredictionResearchApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl || ''}/api/prediction-research`;
  inventory(category: LotteryCategory) {
    return this.http.get<EvidenceInventory>(`${this.base}/inventory`, { params: { category } });
  }
  benchmark(category: LotteryCategory) {
    return this.http.post<BenchmarkReport>(
      `${this.base}/benchmark`,
      { warmupDraws: 10, maximumTargets: 30, trainingWindow: 180, budget: 3 },
      { params: { category } },
    );
  }
}
