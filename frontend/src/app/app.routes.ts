import { Routes } from '@angular/router';
import { PredictionComponent } from './feature/prediction/prediction.component';
import { EntryComponent } from './feature/entry/entry.component';
import { LatestDrawAnalysisComponent } from './feature/latest-draw-analysis/latest-draw-analysis.component';
import { ForecastsComponent } from './feature/forecasts/forecasts.component';
import { PredictionWorkspaceComponent } from './feature/prediction/prediction-workspace.component';
import { AiLabComponent } from './feature/ai-lab/ai-lab.component';

export const routes: Routes = [
  { path: 'ai-lab', component: AiLabComponent },
  { path: 'forecasts', redirectTo: 'analyze/history', pathMatch: 'full' },
  { path: 'analyze', component: PredictionWorkspaceComponent, children: [
    { path: '', component: PredictionComponent, pathMatch: 'full' },
    { path: 'history', component: ForecastsComponent }
  ] },
  { path: 'latest-analysis', component: LatestDrawAnalysisComponent },
  { path: 'entry', component: EntryComponent },
  { path: '', redirectTo: '/analyze', pathMatch: 'full' },
  { path: '**', redirectTo: '/analyze' }
];
