import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = 'Analyze Project';
  activeTab: 'manual' | 'prediction' = 'manual';

  setActiveTab(tab: 'manual' | 'prediction'): void {
    this.activeTab = tab;
  }
}
