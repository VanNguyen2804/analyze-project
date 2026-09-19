import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';

// Feature components
import { PredictionComponent } from './feature/prediction/prediction.component';
import { ManualEntryComponent } from './feature/manual-entry/manual-entry.component';

// Shared components
import { HeaderComponent } from './share/components/header/header.component';
import { LeftMenuComponent } from './share/components/left-menu/left-menu.component';

// Core services
import { PredictionService } from './core/services/prediction.service';
import { LotteryService } from './core/services/lottery.service';
import { CategoryService } from './core/services/category.service';

@NgModule({
  declarations: [
    AppComponent,
    HeaderComponent,
    LeftMenuComponent,
    PredictionComponent,
    ManualEntryComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    FormsModule,
    AppRoutingModule
  ],
  providers: [
    PredictionService,
    LotteryService,
    CategoryService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
