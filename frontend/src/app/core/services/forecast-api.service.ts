import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ForecastMutationResult,
  ForecastOverview,
  LabOverview,
  LabTicket,
  LabRun,
} from '../models/forecast.model';
import { LotteryCategory } from '../models/lottery-category.model';

/** HTTP contracts only; view state belongs to the feature components. */
@Injectable({ providedIn: 'root' })
export class ForecastApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl || '';

  overview(category: LotteryCategory, page: number, fromDate = '', toDate = '') {
    return this.http.get<ForecastOverview>(`${this.base}/api/forecasts/overview`, {
      params: {
        category,
        page,
        size: 50,
        ...(fromDate ? { fromDate } : {}),
        ...(toDate ? { toDate } : {}),
      },
    });
  }
  mutate(action: 'capture' | 'reconcile') {
    return this.http.post<ForecastMutationResult>(`${this.base}/api/forecasts/${action}`, {});
  }
  labOverview(category: LotteryCategory, page: number) {
    return this.http
      .get<LabOverview>(`${this.base}/api/ai-lab`, { params: { category, page } })
      .pipe(timeout(15000));
  }
  labHighlights(category: LotteryCategory) {
    return this.http
      .get<LabTicket[]>(`${this.base}/api/ai-lab/highlights`, { params: { category } })
      .pipe(timeout(15000));
  }
  runLab() {
    return this.http.post<LabRun>(`${this.base}/api/ai-lab/run`, {}).pipe(timeout(60000));
  }
}
