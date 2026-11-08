import { BackendReadinessService } from '../../../core/services/backend-readiness.service';
import { of as readyNow } from 'rxjs';
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
      providers: [
        {
          provide: BackendReadinessService,
          useValue: {
            ready: () => readyNow(undefined),
            waiting: () => false,
            invalidate: () => {},
          },
        },
      ],
    }),
  );
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
    expect(history).toContain('Tổng lịch sử thuật toán: 3 lần trúng ≥3 số');
    expect(history).toContain('(3 vé / 2 kỳ)');
    expect(text).toContain('08/11/2026');
    expect(text).toContain('25/10/2026');
    expect(fixture.nativeElement.querySelectorAll('.numbers span').length).toBe(6);
    expect(fixture.nativeElement.querySelectorAll('.numbers .hit').length).toBe(0);
    TestBed.inject(HttpTestingController).expectNone((r) => r.url.includes('/api/ai-lab'));
    fixture.destroy();
  });
  it('groups five tickets under one algorithm and never sums repeated historical counts', () => {
    const fixture = TestBed.createComponent(LabHighlightsComponent);
    const tickets = Array.from({ length: 5 }, (_, i) => ({ ...current, id: 'new-' + i }));
    fixture.componentRef.setInput('tickets', tickets);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('h3').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('.history-count').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.history-count').textContent).toContain(
      '3 lần trúng',
    );
    expect(fixture.nativeElement.querySelectorAll('.tickets article').length).toBe(5);
    expect(fixture.nativeElement.textContent).toContain('5 dãy cùng thuật toán');
    fixture.destroy();
  });
  it('keeps algorithms and historical evaluation groups distinct under their headings', () => {
    const fixture = TestBed.createComponent(LabHighlightsComponent);
    fixture.componentRef.setInput('tickets', [
      {
        ...current,
        pastPerformance: {
          ...current.pastPerformance,
          cohorts: [
            {
              cohort: 'a',
              cohortTag: 'group-a',
              modelVersion: 'v1',
              bestMatched: 3,
              hitTickets: 1,
              hitDraws: 1,
              latestHitDate: '2026-10-25',
            },
            {
              cohort: 'b',
              cohortTag: 'group-b',
              modelVersion: 'v2',
              bestMatched: 4,
              hitTickets: 2,
              hitDraws: 1,
              latestHitDate: '2026-10-28',
            },
          ],
        },
      },
      {
        ...current,
        id: 'other',
        model: 'recency',
        pastPerformance: { ...current.pastPerformance, hitTickets: 1, hitDraws: 1 },
      },
    ]);
    fixture.detectChanges();
    const groups = fixture.nativeElement.querySelectorAll('.algorithm-group');
    expect(groups.length).toBe(2);
    expect(groups[0].querySelector('h3').textContent).toContain('Logistic không feedback');
    expect(groups[0].querySelectorAll('.history-breakdown li').length).toBe(2);
    expect(groups[0].querySelectorAll('.history-breakdown li')[0].textContent).toContain(
      '1 vé trúng',
    );
    expect(groups[0].querySelectorAll('.history-breakdown li')[1].textContent).toContain(
      '2 vé trúng',
    );
    expect(groups[1].querySelector('h3').textContent).toContain('Tần suất suy giảm');
    expect(groups[1].querySelector('.history-count').textContent).toContain('1 lần trúng');
    fixture.destroy();
  });
  it('cancels old categories, clears their results and never requests training', fakeAsync(() => {
    const fixture = TestBed.createComponent(LabHighlightsComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('remote', true);
    fixture.detectChanges();
    tick(0);
    const old = http.expectOne(
      `${environment.apiUrl || ''}/api/ai-lab/highlights?category=MEGA&async=true`,
    );
    fixture.componentRef.setInput('category', 'POWER');
    fixture.detectChanges();
    tick(0);
    expect(old.cancelled).toBeTrue();
    http
      .expectOne(`${environment.apiUrl || ''}/api/ai-lab/highlights?category=POWER&async=true`)
      .flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Chờ AI Lab cập nhật');
    http.expectNone((r) => r.method === 'POST');
    fixture.destroy();
    http.verify({ ignoreCancelled: true });
  }));
});
