import { Component, EventEmitter, Input, OnInit, OnDestroy, Output } from '@angular/core';
import { Subscription } from 'rxjs';
import { CategoryService } from '../../../core/services/category.service';

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
    .category-btn {
      transition: all 0.2s ease;
      cursor: pointer;
      border: 1px solid #dee2e6;
      background: #ffffff;
    }
    .category-btn:hover {
      transform: translateY(-1px);
    }
    .active-power {
      background: #e7f1ff !important;
      border-color: #0d6efd !important;
    }
    .active-mega {
      background: #ffe3e3 !important;
      border-color: #dc3545 !important;
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
export class LeftMenuComponent implements OnInit, OnDestroy {
  @Input() activeTab?: string;
  @Output() tabChange = new EventEmitter<string>();

  currentCategory: 'MEGA' | 'POWER' = 'POWER';
  private catSub?: Subscription;

  constructor(private categoryService: CategoryService) {}

  ngOnInit(): void {
    this.currentCategory = this.categoryService.currentCategory;
    this.catSub = this.categoryService.category$.subscribe(cat => {
      this.currentCategory = cat;
    });
  }

  ngOnDestroy(): void {
    this.catSub?.unsubscribe();
  }

  selectCategory(cat: 'MEGA' | 'POWER'): void {
    this.categoryService.setCategory(cat);
  }
}
