import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AppComponent } from './app.component';
import { PredictionComponent } from './components/prediction/prediction.component';
import { ManualEntryComponent } from './components/manual-entry/manual-entry.component';
import { PredictionService } from './services/prediction.service';
import { LotteryService } from './services/lottery.service';

@NgModule({
  declarations: [
    AppComponent,
    PredictionComponent,
    ManualEntryComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    FormsModule
  ],
  providers: [
    PredictionService,
    LotteryService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
