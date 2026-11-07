import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { NavigationService } from './core/services/navigation.service';

import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from './share/components/header/header.component';
import { LeftMenuComponent } from './share/components/left-menu/left-menu.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, LeftMenuComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  readonly navigation = inject(NavigationService);
  title = 'Analyze Project';
}
