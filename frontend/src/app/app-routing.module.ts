import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PredictionComponent } from './feature/prediction/prediction.component';
import { ManualEntryComponent } from './feature/manual-entry/manual-entry.component';
import { EntryComponent } from './feature/entry/entry.component';

const routes: Routes = [
  { path: 'analyze', component: PredictionComponent },
  { path: 'entry', component: EntryComponent },
  { path: '', redirectTo: '/analyze', pathMatch: 'full' }, // Mặc định mở trang AI XGBoost
  { path: '**', redirectTo: '/analyze' } // Nhập sai URL sẽ tự động quay về trang chính
];
@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
