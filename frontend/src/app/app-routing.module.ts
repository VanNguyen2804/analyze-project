import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PredictionComponent } from './feature/prediction/prediction.component';
import { ManualEntryComponent } from './feature/manual-entry/manual-entry.component';
import { EntryComponent } from './feature/entry/entry.component';
import { FrenchLearningComponent } from './feature/french-learning/french-learning.component';

const routes: Routes = [
  { path: 'french', component: FrenchLearningComponent }, // Tuyến đường mới
  { path: 'analyze', component: PredictionComponent },
  { path: 'entry', component: EntryComponent },
  { path: '', redirectTo: '/french', pathMatch: 'full' }, // CẬP NHẬT: Trang chủ mặc định là French
  { path: '**', redirectTo: '/french' } // CẬP NHẬT: Lỗi URL trả về trang French
];
@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
