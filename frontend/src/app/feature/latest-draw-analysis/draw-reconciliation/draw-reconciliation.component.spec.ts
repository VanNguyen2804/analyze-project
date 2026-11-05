import { TestBed } from '@angular/core/testing';
import { DrawReconciliationComponent } from './draw-reconciliation.component';
import { DrawEvaluation } from '../../../core/models/draw-report.model';

describe('Read-only draw evaluation', () => {
  it('renders paired one-ticket metrics separately from the five-ticket Lab evaluation', () => {
    TestBed.configureTestingModule({ imports: [DrawReconciliationComponent] });
    const fixture = TestBed.createComponent(DrawReconciliationComponent);
    const evaluation: DrawEvaluation = {
      cohort: 'lab-v1',
      source: 'AI_LAB',
      algorithm: 'lab-logistic',
      modelVersion: 'v1',
      ticketBudget: 1,
      evaluatedDraws: 1,
      firstDate: '2026-10-29',
      lastDate: '2026-10-29',
      averageMatched: 2,
      bestMatchedAverage: 2,
      coverageAverage: 2,
      hit3RatePercent: 0,
      hit4RatePercent: 0,
      frequencyAverageMatched: null,
      randomAverageMatched: null,
      pairedDraws: 0,
      pairedDifferenceVsFrequency: null,
      approximate95PercentInterval: null,
      evidenceStatus: 'INSUFFICIENT_SAMPLE',
    };
    fixture.componentRef.setInput('comparisons', [
      {
        id: 'pair',
        policy: 'frozen-slot-0-v1',
        dates: ['2026-10-29'],
        logistic: evaluation,
        algorithm: {
          ...evaluation,
          cohort: 'algorithm-v1',
          algorithm: 'bayesian_graph',
          averageMatched: 1,
        },
        averageDifference: 1,
        approximate95PercentInterval: null,
      },
    ]);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('1 vé mỗi bên');
    expect(text).toContain('slot 0');
    expect(text).toContain('bayesian_graph');
    expect(text).toContain('1 kỳ chung');
    expect(text).toContain('Mẫu còn ít');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
    expect(text).not.toContain('NaN');
    fixture.destroy();
  });
  it('renders missing evidence without requesting predictions or suggesting a configuration change', () => {
    TestBed.configureTestingModule({ imports: [DrawReconciliationComponent] });
    const fixture = TestBed.createComponent(DrawReconciliationComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Chưa có snapshot');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
    fixture.componentRef.setInput('evaluation', []);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('NaN');
    fixture.destroy();
  });
});
