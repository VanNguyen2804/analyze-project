import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { DrawReconciliationComponent } from './draw-reconciliation.component';

describe('Draw reconciliation', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [DrawReconciliationComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }),
  );

  it('cancels old category reads and outstanding requests when leaving the component', () => {
    const fixture = TestBed.createComponent(DrawReconciliationComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('category', 'MEGA');
    fixture.detectChanges();
    const old = http.expectOne((r) => r.url.includes('/reconcile-5-draws'));
    const oldRecommendations = http.expectOne((r) => r.url.includes('/predict'));
    fixture.componentRef.setInput('category', 'POWER');
    fixture.detectChanges();
    expect(old.cancelled).toBeTrue();
    expect(oldRecommendations.cancelled).toBeTrue();
    const next = http.expectOne((r) => r.url.includes('/reconcile-5-draws'));
    const nextRecommendations = http.expectOne((r) => r.url.includes('/predict'));
    expect(next.request.url).toContain('category=POWER');
    fixture.destroy();
    expect(next.cancelled).toBeTrue();
    expect(nextRecommendations.cancelled).toBeTrue();
    http.verify({ ignoreCancelled: true });
  });

  it('reports the version observed by reconciliation without writing a configuration', () => {
    const fixture = TestBed.createComponent(DrawReconciliationComponent);
    const http = TestBed.inject(HttpTestingController);
    const changed = jasmine.createSpy('versionChanged');
    fixture.componentInstance.versionChanged.subscribe(changed);
    fixture.componentRef.setInput('category', 'MEGA');
    fixture.detectChanges();
    http
      .expectOne((r) => r.url.includes('/reconcile-5-draws'))
      .flush({ draws: [], totalDrawsInDb: 10, currentHyperparameterVersion: 'v2' });
    http.expectOne((r) => r.url.includes('/predict')).flush({});
    expect(changed).toHaveBeenCalledWith('v2');
    expect(fixture.componentInstance.availableLimits).toEqual([5, 10]);
    http.expectNone((r) => r.method === 'POST');
    fixture.destroy();
    http.verify();
  });
});
