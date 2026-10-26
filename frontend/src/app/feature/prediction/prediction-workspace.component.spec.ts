import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { routes } from '../../app.routes';
import { AnalyzeService } from '../../core/services/analyze.service';
import { environment } from '../../../environments/environment';

@Component({ standalone: true, template: '<div class="prediction-stub">Tạo dự đoán</div>' })
class PredictionStub {}

describe('Prediction workspace routing', () => {
  let http: HttpTestingController;
  const overview = (category: string) =>
    `${environment.apiUrl || ''}/api/forecasts/overview?category=${category}&page=0&size=50`;
  beforeEach(() => {
    const testRoutes = routes.map((route) =>
      route.path === 'analyze'
        ? {
            ...route,
            children: route.children!.map((child) =>
              child.path === ''
                ? { ...child, loadComponent: undefined, component: PredictionStub }
                : child,
            ),
          }
        : route,
    );
    TestBed.configureTestingModule({
      providers: [provideRouter(testRoutes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('opens the creation tab without requesting history and preserves category when switching tabs', async () => {
    const harness = await RouterTestingHarness.create('/analyze');
    expect(harness.routeNativeElement!.querySelector('.prediction-stub')).not.toBeNull();
    http.expectNone(overview('MEGA'));
    TestBed.inject(AnalyzeService).setCategory('POWER');
    await harness.navigateByUrl('/analyze/history');
    http
      .expectOne(overview('POWER'))
      .flush({ rows: [], totalRows: 0, evaluation: { groups: [] }, enabled: true });
    expect(harness.routeNativeElement!.querySelector('.prediction-stub')).toBeNull();
    expect(harness.routeNativeElement!.querySelector('.history-view')).not.toBeNull();
    await harness.navigateByUrl('/analyze');
    expect(harness.routeNativeElement!.querySelector('.history-view')).toBeNull();
    expect(TestBed.inject(AnalyzeService)).toBeTruthy();
  });
  it('redirects the existing forecasts link to the history tab with one request', async () => {
    const harness = await RouterTestingHarness.create('/forecasts');
    expect(TestBed.inject(Router).url).toBe('/analyze/history');
    http
      .expectOne(overview('MEGA'))
      .flush({ rows: [], totalRows: 0, evaluation: { groups: [] }, enabled: true });
    expect(harness.routeNativeElement!.querySelector('.history-view')).not.toBeNull();
  });
});
