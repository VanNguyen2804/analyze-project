import { DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { AnalyzeService } from '../../../core/services/analyze.service';
import { LinkageTimelineComponent } from '../linkage-timeline/linkage-timeline.component';
import { PredictionChartComponent } from './prediction-chart.component';

describe('Prediction chart', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [PredictionChartComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
      deferBlockBehavior: DeferBlockBehavior.Manual,
    }),
  );

  it('loads history once and keeps timeline filters when switching chart modes', async () => {
    const fixture = TestBed.createComponent(PredictionChartComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http
      .expectOne((r) => r.url.includes('/history?category=MEGA'))
      .flush([
        { id: 1, drawDate: '2026-10-21', numbers: [1, 2, 3, 4, 5, 6] },
        { id: 2, drawDate: '2026-10-23', numbers: [2, 4, 6, 8, 10, 12] },
      ]);
    const blocks = await fixture.getDeferBlocks();
    await blocks[0].render(DeferBlockState.Complete);
    fixture.detectChanges();
    await fixture.whenStable();
    const timeline = fixture.debugElement.query(By.directive(LinkageTimelineComponent));
    timeline.nativeElement.querySelectorAll('input[type="checkbox"]')[1].click();
    await fixture.whenStable();
    expect(timeline.componentInstance.showRepeatLinks).toBeFalse();
    expect(getComputedStyle(timeline.nativeElement.querySelector('.flow-ball-number')).width).toBe(
      '34px',
    );
    fixture.componentInstance.setChartMode('gap_pressure');
    await blocks[1].render(DeferBlockState.Complete);
    fixture.detectChanges();
    expect(getComputedStyle(timeline.nativeElement).display).toBe('none');
    fixture.componentInstance.setChartMode('timeline');
    fixture.detectChanges();
    expect(
      fixture.debugElement.query(By.directive(LinkageTimelineComponent)).componentInstance,
    ).toBe(timeline.componentInstance);
    expect(timeline.componentInstance.showRepeatLinks).toBeFalse();
    http.expectNone((r) => r.url.includes('/history'));
    fixture.destroy();
    http.verify();
  });

  it('cancels history for an old category and pending reads on destruction', () => {
    const fixture = TestBed.createComponent(PredictionChartComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const old = http.expectOne((r) => r.url.includes('/history?category=MEGA'));
    TestBed.inject(AnalyzeService).setCategory('POWER');
    expect(old.cancelled).toBeTrue();
    const next = http.expectOne((r) => r.url.includes('/history?category=POWER'));
    fixture.destroy();
    expect(next.cancelled).toBeTrue();
    http.verify({ ignoreCancelled: true });
  });
});
