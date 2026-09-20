import { Routes } from '@angular/router';
import { FrenchLearningComponent } from './feature/french-learning/french-learning.component';
import { PredictionComponent } from './feature/prediction/prediction.component';
import { EntryComponent } from './feature/entry/entry.component';

export const routes: Routes = [
  { path: 'french', component: FrenchLearningComponent },
  { path: 'analyze', component: PredictionComponent },
  { path: 'entry', component: EntryComponent },
  { path: '', redirectTo: '/french', pathMatch: 'full' },
  { path: '**', redirectTo: '/french' }
];
