import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { BackendReadinessService } from '../../core/services/backend-readiness.service';
import { LabPredictionGroup, LabPredictions } from '../../core/models/forecast.model';
import { PredictionComponent } from './prediction.component';
import { environment } from '../../../environments/environment';

const target = '2099-11-13';
function group(
  model: string,
  percentage: number | null,
  hits: number,
  draws: number,
): LabPredictionGroup {
  return {
    model,
    cohort: model,
    modelVersion: 'v2',
    targetDate: target,
    tickets: [
      {
        id: model,
        model,
        targetDate: target,
        createdAt: '2099-11-11T08:00:00Z',
        numbers: [3, 14, 22, 27, 41, 43],
      },
    ],
    performance: {
      evaluatedDraws: draws,
      evaluatedTickets: draws * 5,
      hitTickets: hits,
      bestMatched: hits ? 3 : 0,
      latestHitDate: null,
      levels: [3, 2, 1].map((threshold) => ({
        threshold,
        drawHits: hits,
        drawPercent: percentage,
      })),
    },
    history: [],
  };
}
function response(
  groups: LabPredictionGroup[],
  category: 'MEGA' | 'POWER' = 'MEGA',
): LabPredictions {
  return { category, targetDate: target, asOf: '2099-11-11T10:00:00Z', groups };
}
describe('AI Lab prediction page', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: BackendReadinessService,
          useValue: { ready: () => of(undefined), invalidate: () => {} },
        },
      ],
    }),
  );
  const endpoint = (category: string) =>
    `${environment.apiUrl || ''}/api/ai-lab/predictions?category=${category}&async=true`;

  it('shows ranked Lab models with three rates and denominators, without requesting legacy algorithms', fakeAsync(() => {
    const fixture = TestBed.createComponent(PredictionComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    tick(0);
    http
      .expectOne(endpoint('MEGA'))
      .flush(
        response([
          group('logistic_feedback_coverage', 100, 1, 1),
          group('recency', 28.6, 2, 7),
          group('logistic', null, 0, 0),
        ]),
      );
    fixture.detectChanges();
    const cards = fixture.nativeElement.querySelectorAll('.model-card');
    expect(cards.length).toBe(3);
    expect(cards[0].querySelector('h2').textContent).toContain('phản hồi');
    expect(cards[1].textContent).toContain('28.6%');
    expect(cards[1].textContent).toContain('2/7 kỳ');
    expect(cards[0].querySelectorAll('.rate').length).toBe(3);
    expect(cards[2].textContent).toContain('Chưa có');
    expect(fixture.nativeElement.querySelector('app-user-desk')).toBeNull();
    http.expectNone((req) => req.method === 'POST' || req.url.includes('/api/analyze'));
    fixture.destroy();
    http.verify();
  }));

  it('opens the selected slot history and closes the popup', fakeAsync(() => {
    const fixture = TestBed.createComponent(PredictionComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges(); tick(0);
    const model = group('recency', 50, 2, 4);
    model.ticketPriorities = [{ ticketId: 'recency', slot: 2, rank: 1, performance: model.performance,
      timeline: [{ targetDate: '2026-11-11', numbers: [1,2,3,7,8,9], actualNumbers: [1,2,3,4,5,6], matched: 3, modelVersion: 'v1' }] }];
    http.expectOne(endpoint('MEGA')).flush(response([model])); fixture.detectChanges();
    fixture.nativeElement.querySelector('.history-trigger').click(); fixture.detectChanges();
    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    expect(dialog.open).toBeTrue();
    expect(dialog.textContent).toContain('Dãy 3 · Ưu tiên 1');
    expect(dialog.textContent).toContain('3/6');
    expect(dialog.textContent).toContain('v1');
    (dialog.querySelector('button') as HTMLButtonElement).click(); fixture.detectChanges();
    expect(dialog.open).toBeFalse();
    fixture.destroy(); http.verify();
  }));

  it('cancels the previous category and clears its tickets before loading POWER', fakeAsync(() => {
    const fixture = TestBed.createComponent(PredictionComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    tick(0);
    const previous = http.expectOne(endpoint('MEGA'));
    fixture.componentInstance.selectCategory('POWER');
    fixture.detectChanges();
    tick(0);
    expect(previous.cancelled).toBeTrue();
    expect(fixture.nativeElement.querySelectorAll('.ticket').length).toBe(0);
    http.expectOne(endpoint('POWER')).flush(response([], 'POWER'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Chưa có dãy AI Lab');
    fixture.destroy();
    http.verify({ ignoreCancelled: true });
  }));

  it('rejects mismatched categories, supports retry and hides expired tickets', fakeAsync(() => {
    const fixture = TestBed.createComponent(PredictionComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    tick(0);
    http.expectOne(endpoint('MEGA')).flush(response([group('recency', 100, 1, 1)], 'POWER'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.ticket').length).toBe(0);
    fixture.componentInstance.refresh();
    const expired = group('recency', 100, 1, 1);
    expired.targetDate = '2020-11-13';
    http
      .expectOne(endpoint('MEGA'))
      .flush({ ...response([expired]), targetDate: expired.targetDate });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.ticket').length).toBe(0);
    fixture.destroy();
    http.verify();
  }));

  it('copies the selected Lab ticket and reports success only after completion', async () => {
    const fixture = TestBed.createComponent(PredictionComponent);
    const original = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
    let complete!: () => void;
    const writeText = jasmine
      .createSpy()
      .and.returnValue(new Promise<void>((resolve) => (complete = resolve)));
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    try {
      const pending = fixture.componentInstance.copyTicket([3, 14, 22, 27, 41, 43]);
      expect(writeText).toHaveBeenCalledWith('03 - 14 - 22 - 27 - 41 - 43');
      expect(fixture.componentInstance.copyMessage()).toBe('');
      complete();
      await pending;
      expect(fixture.componentInstance.copyMessage()).toContain('Đã sao chép');
    } finally {
      if (original) Object.defineProperty(navigator, 'clipboard', original);
      else Reflect.deleteProperty(navigator, 'clipboard');
      fixture.destroy();
    }
  });
});
