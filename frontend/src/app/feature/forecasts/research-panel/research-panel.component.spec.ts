import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ResearchPanelComponent } from './research-panel.component';

describe('Research panel evidence boundaries', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }),
  );
  it('reads inventory without fitting and cancels work when the category changes', () => {
    const fixture = TestBed.createComponent(ResearchPanelComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('category', 'MEGA');
    fixture.detectChanges();
    const inventory = http.expectOne(
      (r) => r.url.endsWith('/inventory') && r.params.get('category') === 'MEGA',
    );
    http.expectNone((r) => r.url.endsWith('/benchmark'));
    fixture.componentInstance.run();
    fixture.componentInstance.run();
    const study = http.expectOne((r) => r.url.endsWith('/benchmark'));
    expect(study.request.method).toBe('POST');
    expect(study.request.body.budget).toBe(3);
    fixture.componentRef.setInput('category', 'POWER');
    fixture.detectChanges();
    expect(inventory.cancelled).toBeTrue();
    expect(study.cancelled).toBeTrue();
    expect(fixture.componentInstance.report()).toBeNull();
    expect(fixture.componentInstance.running()).toBeFalse();
    http.expectOne((r) => r.params.get('category') === 'POWER').flush(null);
    fixture.destroy();
    http.verify({ ignoreCancelled: true });
  });
  it('does not hide failures or retry expensive studies automatically', () => {
    const fixture = TestBed.createComponent(ResearchPanelComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('category', 'MEGA');
    fixture.detectChanges();
    http
      .expectOne((r) => r.url.endsWith('/inventory'))
      .flush({}, { status: 503, statusText: 'Unavailable' });
    expect(fixture.componentInstance.error()).toContain('Chưa tải');
    fixture.componentInstance.run();
    http
      .expectOne((r) => r.url.endsWith('/benchmark'))
      .flush({ status: 'INSUFFICIENT_DATA', summary: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Chưa đủ kỳ');
    fixture.destroy();
    http.verify();
  });
});
