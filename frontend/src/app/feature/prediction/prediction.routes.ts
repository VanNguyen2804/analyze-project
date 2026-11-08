import { Routes } from '@angular/router';

export const predictionRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./prediction-workspace.component').then((m) => m.PredictionWorkspaceComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./prediction.component').then((m) => m.PredictionComponent),
      },
      {
        path: 'review',
        loadComponent: () =>
          import('../latest-draw-analysis/latest-draw-analysis.component').then(
            (m) => m.LatestDrawAnalysisComponent,
          ),
      },
      {
        path: 'history',
        loadComponent: () =>
          import('../forecasts/forecasts.component').then((m) => m.ForecastsComponent),
      },
    ],
  },
];
