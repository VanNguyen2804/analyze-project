import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class NavigationService {
  readonly open = signal(false);
  toggle() {
    if (this.open()) this.close();
    else {
      this.open.set(true);
      setTimeout(() => document.querySelector<HTMLAnchorElement>('#primary-navigation a')?.focus());
    }
  }
  close() {
    const wasOpen = this.open();
    this.open.set(false);
    if (wasOpen) document.getElementById('navigation-toggle')?.focus();
  }
}
