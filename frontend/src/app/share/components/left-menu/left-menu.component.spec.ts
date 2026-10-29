import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { LeftMenuComponent } from './left-menu.component';
import { CategoryService } from '../../../core/services/category.service';

describe('Latest result badge', () => {
  it('only reads the result and cancels the old category instead of running backtests', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController),
      fixture = TestBed.createComponent(LeftMenuComponent);
    fixture.detectChanges();
    const old = http.expectOne((r) => r.url.endsWith('/latest-draw'));
    TestBed.inject(CategoryService).setCategory('POWER');
    expect(old.cancelled).toBeTrue();
    http
      .expectOne((r) => r.params.get('category') === 'POWER')
      .flush({ drawDate: '2026-10-29', numbers: [9, 29, 33, 38, 45, 46], specialNumber: 53 });
    expect(fixture.componentInstance.latestDrawBadge()).toContain('Phụ 53');
    http.expectNone((r) => r.url.includes('/reconcile') || r.method !== 'GET');
    fixture.destroy();
    http.verify({ ignoreCancelled: true });
  });
});
