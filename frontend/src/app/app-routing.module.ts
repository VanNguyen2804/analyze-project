import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PredictionComponent } from './feature/prediction/prediction.component';
import { ManualEntryComponent } from './feature/manual-entry/manual-entry.component';

const routes: Routes = [
  // Mặc định chuyển hướng tới /analyze (trang chủ phân tích AI)
  { path: '', redirectTo: 'analyze', pathMatch: 'full' },
  { path: 'landing', redirectTo: 'analyze', pathMatch: 'full' },
  { path: 'analyze', component: PredictionComponent, title: 'Trang chủ - Phân tích & Dự đoán AI' },
  { path: 'entry', component: ManualEntryComponent, title: 'Nhập dãy số theo ngày' },
  { path: 'manual', redirectTo: 'entry', pathMatch: 'full' },
  // Điều hướng mọi path không hợp lệ về /analyze
  { path: '**', redirectTo: 'analyze' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
