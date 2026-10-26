import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'ai-lab',
    loadComponent: () => import('./feature/ai-lab/ai-lab.component').then((m) => m.AiLabComponent),
  },
  { path: 'forecasts', redirectTo: 'analyze/history', pathMatch: 'full' },
  {
    path: 'analyze',
    loadComponent: () =>
      import('./feature/prediction/prediction-workspace.component').then(
        (m) => m.PredictionWorkspaceComponent,
      ),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./feature/prediction/prediction.component').then((m) => m.PredictionComponent),
        pathMatch: 'full',
      },
      {
        path: 'history',
        loadComponent: () =>
          import('./feature/forecasts/forecasts.component').then((m) => m.ForecastsComponent),
      },
    ],
  },
  {
    path: 'latest-analysis',
    loadComponent: () =>
      import('./feature/latest-draw-analysis/latest-draw-analysis.component').then(
        (m) => m.LatestDrawAnalysisComponent,
      ),
  },
  {
    path: 'entry',
    loadComponent: () => import('./feature/entry/entry.component').then((m) => m.EntryComponent),
  },
  { path: '', redirectTo: '/analyze', pathMatch: 'full' },
  { path: '**', redirectTo: '/analyze' },
];
