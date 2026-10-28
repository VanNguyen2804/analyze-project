import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { WorkspaceBatch, WorkspaceSuggestion } from '../models/workspace.model';

@Injectable({ providedIn: 'root' })
export class WorkspaceApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl || ''}/api/prediction-workspace`;
  combine(id: string, primaryWeight: number, keep: number[], exclude: number[]) {
    return this.http.post<WorkspaceSuggestion>(
      `${this.url}/suggestions/${encodeURIComponent(id)}/combine`,
      { primaryWeight, keep, exclude },
    );
  }
  commit(body: {
    category: string;
    targetDate: string;
    suggestionId: string;
    expectedRevision: number;
    requestKey: string;
    labCount: number;
    tickets: { sourceId: string | null; numbers: number[] }[];
  }) {
    return this.http.post<WorkspaceBatch>(`${this.url}/commit`, body);
  }
  history(category: string) {
    return this.http.get<{ batches: WorkspaceBatch[] }>(`${this.url}/history`, {
      params: { category },
    });
  }
  reconcile() {
    return this.http.post(`${this.url}/reconcile`, {});
  }
}
