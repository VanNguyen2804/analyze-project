import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NoPreloading, provideRouter, Router, withPreloading } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { routes } from '../../app.routes';
import { AppComponent } from '../../app.component';
import { NavigationService } from '../../core/services/navigation.service';
import { WelcomeComponent } from './welcome.component';

describe('Welcome landing page', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withPreloading(NoPreloading)),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ lang: 'vi' }),
      ],
    }),
  );

  it('renders the full shell at home without any feature API request and opens the menu', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/');
    fixture.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/landing');
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Analyze.');
    expect(fixture.nativeElement.querySelector('app-prediction')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-stocks')).toBeNull();
    TestBed.inject(HttpTestingController).expectNone((request) => request.url.includes('/api/'));
    fixture.nativeElement.querySelector('app-welcome button').click();
    fixture.detectChanges();
    expect(TestBed.inject(NavigationService).open()).toBeTrue();
    fixture.destroy();
  });

  it('loads the entry feature only after navigation from home', async () => {
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create('/landing');
    expect(harness.routeDebugElement?.componentInstance instanceof WelcomeComponent).toBeTrue();
    http.expectNone((request) => request.url.includes('/api/'));
    await harness.navigateByUrl('/entry');
    expect(harness.routeNativeElement?.querySelector('input')).not.toBeNull();
    http.expectOne((request) => request.url.includes('/api/analyze/history')).flush([]);
    http.expectOne((request) => request.url.includes('/api/analyze/user-history')).flush([]);
    http.expectNone((request) => /suggestions|stocks|overview/.test(request.url));
    await harness.navigateByUrl('/landing');
    expect(harness.routeDebugElement?.componentInstance instanceof WelcomeComponent).toBeTrue();
    http.verify();
  });
});
