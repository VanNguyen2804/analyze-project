import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-left-menu',
  templateUrl: './left-menu.component.html',
  styles: [`
    .left-menu {
      width: 260px;
      min-height: calc(100vh - 64px);
      background: #ffffff;
      border-right: 1px solid #e9ecef;
    }
    .menu-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 18px;
      border-radius: 8px;
      color: #495057;
      text-decoration: none;
      font-weight: 500;
      transition: all 0.2s ease;
      cursor: pointer;
      border: none;
      background: transparent;
      width: 100%;
      text-align: left;
    }
    .menu-item:hover {
      background-color: #f8f9fa;
      color: #0d6efd;
    }
    .menu-item.active {
      background-color: #e7f1ff;
      color: #0d6efd;
      font-weight: 600;
    }
    @media (max-width: 767.98px) {
      .left-menu {
        width: 100%;
        min-height: auto;
        border-right: none;
        border-bottom: 1px solid #e9ecef;
      }
    }
  `]
})
export class LeftMenuComponent {
  @Input() activeTab: 'manual' | 'prediction' = 'manual';
  @Output() tabChange = new EventEmitter<'manual' | 'prediction'>();

  selectTab(tab: 'manual' | 'prediction'): void {
    this.tabChange.emit(tab);
  }
}
