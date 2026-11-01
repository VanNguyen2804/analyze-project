import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { MegaPortfolioOverview, MegaPortfolioRun } from '../models/mega-portfolio.model';

@Injectable({ providedIn: 'root' })
export class MegaPortfolioApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl || ''}/api/mega-portfolio`;

  overview(page: number) {
    return this.http.get<MegaPortfolioOverview>(this.base, { params: { page } });
  }

  run() {
    return this.http.post<MegaPortfolioRun>(`${this.base}/run`, {});
  }
}
