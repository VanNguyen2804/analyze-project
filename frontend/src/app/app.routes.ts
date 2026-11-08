import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'stocks',
    loadComponent: () => import('./feature/stocks/stocks.component').then((m) => m.StocksComponent),
  },
  {
    path: 'stocks/:symbol',
    loadComponent: () => import('./feature/stocks/stocks.component').then((m) => m.StocksComponent),
  },
  {
    path: 'ai-lab',
    loadComponent: () => import('./feature/ai-lab/ai-lab.component').then((m) => m.AiLabComponent),
  },
  { path: 'forecasts', redirectTo: 'analyze/history', pathMatch: 'full' },
  {
    path: 'analyze',
    loadChildren: () =>
      import('./feature/prediction/prediction.routes').then((m) => m.predictionRoutes),
  },
  {
    path: 'latest-analysis',
    redirectTo: '/analyze/review',
    pathMatch: 'full',
  },
  {
    path: 'entry',
    loadComponent: () => import('./feature/entry/entry.component').then((m) => m.EntryComponent),
  },
  {
    path: 'landing',
    loadComponent: () =>
      import('./feature/welcome/welcome.component').then((m) => m.WelcomeComponent),
  },
  { path: '', redirectTo: '/landing', pathMatch: 'full' },
  { path: '**', redirectTo: '/landing' },
];
