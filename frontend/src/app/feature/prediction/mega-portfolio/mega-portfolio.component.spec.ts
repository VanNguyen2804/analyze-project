import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MegaPortfolioComponent } from './mega-portfolio.component';
import { MegaPortfolioOverview } from '@app/core/models/mega-portfolio.model';
import { environment } from '../../../../environments/environment';

describe('MEGA prospective portfolio experiment', () => {
  let http: HttpTestingController;
  const url = `${environment.apiUrl || ''}/api/mega-portfolio`;
  const empty = (): MegaPortfolioOverview => ({
    version: 'mega-portfolio-v1',
    category: 'MEGA',
    budget: 3,
    enabled: true,
    targetDate: '2026-11-04',
    lastRun: { status: 'NOT_RUN' },
    current: null,
    page: 0,
    totalRows: 0,
    excludedInvalidSnapshots: 0,
    rows: [],
    groups: [],
  });
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('only reads on opening and leaves missing tickets and metrics empty', () => {
    const fixture = TestBed.createComponent(MegaPortfolioComponent);
    fixture.detectChanges();
    http.expectOne(`${url}?page=0`).flush(empty());
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Chưa chốt bộ thử nghiệm');
    expect(host.querySelectorAll('.trial-ticket').length).toBe(0);
    expect(host.querySelector('table')).toBeNull();
    http.expectNone((r) => r.method === 'POST');
    fixture.destroy();
  });

  it('displays saved tickets, emits the exact copied ticket and keeps pending metrics absent', () => {
    const fixture = TestBed.createComponent(MegaPortfolioComponent);
    fixture.detectChanges();
    const data = empty();
    const tickets = [
      [1, 2, 3, 4, 5, 6],
      [7, 8, 9, 10, 11, 12],
      [13, 14, 15, 16, 17, 18],
    ];
    data.current = {
      id: 'saved',
      targetDate: '2026-11-04',
      createdAt: '2026-11-01T12:01:00Z',
      trainingThrough: '2026-11-01',
      codeVersion: 'test',
      status: 'PENDING',
      actualNumbers: null,
      arms: [
        {
          id: 'portfolio',
          label: 'Giảm trùng vé',
          tickets,
          uniqueNumbers: 18,
          maxPairOverlap: 0,
          metrics: null,
        },
      ],
    };
    data.groups = [
      {
        cohort: 'test',
        codeVersion: 'test',
        savedDraws: 1,
        evaluatedDraws: 0,
        firstTargetDate: '2026-11-04',
        lastTargetDate: '2026-11-04',
        status: 'COLLECTING',
        arms: [
          {
            id: 'portfolio',
            label: 'Giảm trùng vé',
            hit3Draws: 0,
            hit3Percent: null,
            averageMatched: null,
            averageResultCoverage: null,
          },
        ],
      },
    ];
    http.expectOne(`${url}?page=0`).flush(data);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('.trial-ticket').length).toBe(3);
    expect(host.querySelector('tbody')!.textContent).toContain('Chưa có');
    expect(host.querySelector('tbody')!.textContent).not.toContain('0.0%');
    const copied = jasmine.createSpy();
    fixture.componentInstance.copied.subscribe(copied);
    (host.querySelector('.trial-ticket button') as HTMLButtonElement).click();
    expect(copied).toHaveBeenCalledWith(tickets[0]);
    fixture.destroy();
  });

  it('requires an explicit action to run, prevents duplicate submission and cancels reads on destroy', () => {
    const fixture = TestBed.createComponent(MegaPortfolioComponent);
    fixture.detectChanges();
    http.expectOne(`${url}?page=0`).flush(empty());
    fixture.detectChanges();
    fixture.componentInstance.run();
    fixture.componentInstance.run();
    const request = http.expectOne(`${url}/run`);
    expect(request.request.method).toBe('POST');
    request.flush({ status: 'SAVED' });
    const refresh = http.expectOne(`${url}?page=0`);
    fixture.destroy();
    expect(refresh.cancelled).toBeTrue();
  });
});
