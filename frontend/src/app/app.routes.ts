import { Routes } from '@angular/router';
import { FrenchLearningComponent } from './feature/french-learning/french-learning.component';
import { PredictionComponent } from './feature/prediction/prediction.component';
import { EntryComponent } from './feature/entry/entry.component';
import { LatestDrawAnalysisComponent } from './feature/latest-draw-analysis/latest-draw-analysis.component';

export const routes: Routes = [
  { path: 'french', component: FrenchLearningComponent },
  { path: 'analyze', component: PredictionComponent },
  { path: 'latest-analysis', component: LatestDrawAnalysisComponent },
  { path: 'entry', component: EntryComponent },
  { path: '', redirectTo: '/french', pathMatch: 'full' },
  { path: '**', redirectTo: '/french' }
];
