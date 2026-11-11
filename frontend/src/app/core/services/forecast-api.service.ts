import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { PageJob, PageJobApiService } from './page-job-api.service';
import { environment } from '../../../environments/environment';
import {
  ForecastMutationResult,
  ForecastOverview,
  LabOverview,
  LabTicket,
  LabRun,
  LabPredictions,
} from '../models/forecast.model';
import { LotteryCategory } from '../models/lottery-category.model';

/** HTTP contracts only; view state belongs to the feature components. */
@Injectable({ providedIn: 'root' })
export class ForecastApiService {
  private readonly http = inject(HttpClient);
  private readonly jobs = inject(PageJobApiService);
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
    return this.jobs.follow(
      this.http.get<LabOverview | PageJob<LabOverview>>(`${this.base}/api/ai-lab`, {
        params: { category, page, async: true },
      }),
    );
  }
  labHighlights(category: LotteryCategory) {
    return this.jobs.follow(
      this.http.get<LabTicket[] | PageJob<LabTicket[]>>(`${this.base}/api/ai-lab/highlights`, {
        params: { category, async: true },
      }),
    );
  }
  labPredictions(category: LotteryCategory) {
    return this.jobs.follow(
      this.http.get<LabPredictions | PageJob<LabPredictions>>(
        `${this.base}/api/ai-lab/predictions`,
        {
          params: { category, async: true },
        },
      ),
    );
  }
  runLab() {
    return this.jobs.follow(
      this.http.post<LabRun | PageJob<LabRun>>(
        `${this.base}/api/ai-lab/run`,
        {},
        { params: { async: true } },
      ),
    );
  }
}
