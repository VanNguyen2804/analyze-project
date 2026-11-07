import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LabHighlightsComponent } from './lab-highlights.component';
import { environment } from '../../../../environments/environment';
const current = {
  id: 'new',
  model: 'logistic_no_feedback',
  targetDate: '2026-11-08',
  createdAt: '2026-11-07T09:00:00Z',
  numbers: [10, 11, 12, 13, 14, 15],
  pastPerformance: { bestMatched: 3, hitTickets: 3, hitDraws: 2, latestHitDate: '2026-10-25' },
};
describe('Current predictions from successful AI Lab algorithms', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }),
  );
  it('shows new numbers with algorithm and separate historical evidence, without treating them as hits', () => {
    const fixture = TestBed.createComponent(LabHighlightsComponent);
    fixture.componentRef.setInput('tickets', [current]);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Logistic không feedback');
    const history = fixture.nativeElement
      .querySelector('.history-count')
      .textContent.replace(/\s+/g, ' ')
      .trim();
    expect(history).toContain('Thuật toán đã trúng ≥3 số: 3 lần');
    expect(history).toContain('(3 vé / 2 kỳ)');
    expect(text).toContain('08/11/2026');
    expect(text).toContain('25/10/2026');
    expect(fixture.nativeElement.querySelectorAll('.numbers span').length).toBe(6);
    expect(fixture.nativeElement.querySelectorAll('.numbers .hit').length).toBe(0);
    TestBed.inject(HttpTestingController).expectNone((r) => r.url.includes('/api/ai-lab'));
    fixture.destroy();
  });
  it('cancels old categories, clears their results and never requests training', fakeAsync(() => {
    const fixture = TestBed.createComponent(LabHighlightsComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('remote', true);
    fixture.detectChanges();
    tick(0);
    const old = http.expectOne(`${environment.apiUrl || ''}/api/ai-lab/highlights?category=MEGA`);
    fixture.componentRef.setInput('category', 'POWER');
    fixture.detectChanges();
    tick(0);
    expect(old.cancelled).toBeTrue();
    http.expectOne(`${environment.apiUrl || ''}/api/ai-lab/highlights?category=POWER`).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Chờ AI Lab cập nhật');
    http.expectNone((r) => r.method === 'POST');
    fixture.destroy();
    http.verify({ ignoreCancelled: true });
  }));
});
