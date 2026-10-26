import { TestBed, ComponentFixture, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AiLabComponent } from './ai-lab.component';
import { environment } from '../../../environments/environment';

describe('AI lab', () => {
  let fixture: ComponentFixture<AiLabComponent>;
  let http: HttpTestingController;
  const base = `${environment.apiUrl || ''}/api/ai-lab`;
  const payload = {
    budget: 5,
    page: 0,
    enabled: true,
    rows: [],
    groups: [],
    totalRows: 0,
    lastRun: { status: 'NOT_RUN' },
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AiLabComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(AiLabComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify({ ignoreCancelled: true });
  });
  it('loads read-only data and cancels category requests', () => {
    const old = http.expectOne(`${base}?category=MEGA&page=0`);
    fixture.componentInstance.select('POWER');
    expect(old.cancelled).toBeTrue();
    http.expectOne(`${base}?category=POWER&page=0`).flush(payload);
    http.expectNone(`${base}/run`);
  });
  it('prevents duplicate submissions and refreshes after a run', () => {
    http.expectOne(`${base}?category=MEGA&page=0`).flush(payload);
    fixture.componentInstance.run();
    fixture.componentInstance.run();
    const run = http.expectOne(`${base}/run`);
    expect(run.request.method).toBe('POST');
    run.flush({ status: 'COMPLETED' });
    http.expectOne(`${base}?category=MEGA&page=0`).flush(payload);
    expect(fixture.componentInstance.mutating()).toBeFalse();
  });
  it('shows actual matching numbers and keeps unmeasured values empty', () => {
    http.expectOne(`${base}?category=MEGA&page=0`).flush({
      ...payload,
      totalRows: 1,
      rows: [
        {
          id: 'a',
          model: 'logistic',
          targetDate: '2026-10-28',
          createdAt: '2026-10-27T01:00:00Z',
          numbers: [1, 2, 3, 4, 5, 6],
          settlement: { status: 'SETTLED', matched: 3, actualNumbers: [1, 2, 3, 20, 21, 22] },
        },
      ],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.numbers .hit').length).toBe(3);
    expect(fixture.componentInstance.percent(null)).toBe('Chưa có');
  });
  it('refreshes every minute without creating tickets on GET', fakeAsync(() => {
    http.expectOne(`${base}?category=MEGA&page=0`).flush(payload);
    fixture.destroy();
    fixture = TestBed.createComponent(AiLabComponent);
    fixture.detectChanges();
    http.expectOne(`${base}?category=MEGA&page=0`).flush(payload);
    tick(60000);
    http.expectOne(`${base}?category=MEGA&page=0`).flush(payload);
    http.expectNone(`${base}/run`);
    expect(fixture.componentInstance.data()).toEqual(payload);
    fixture.destroy();
  }));
});
