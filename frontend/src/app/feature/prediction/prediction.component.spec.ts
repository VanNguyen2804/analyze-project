import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { PredictionComponent } from './prediction.component';
import { AnalyzeService } from '@app/core/services/analyze.service';

describe('Prediction request category isolation', () => {
  it('copies six POWER numbers and waits for clipboard completion before reporting success', async () => {
    TestBed.configureTestingModule({ providers: [{ provide: AnalyzeService, useValue: {} }] });
    const fixture = TestBed.createComponent(PredictionComponent);
    const original = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
    let complete!: () => void;
    const writeText = jasmine.createSpy().and.returnValue(
      new Promise<void>((resolve) => {
        complete = resolve;
      }),
    );
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    try {
      const pending = fixture.componentInstance.copyTicket([1, 2, 3, 4, 5, 6], 55);
      expect(writeText).toHaveBeenCalledWith('01 - 02 - 03 - 04 - 05 - 06');
      expect(fixture.componentInstance.copyTicketSuccessMsg).toBeNull();
      complete();
      await pending;
      expect(fixture.componentInstance.copyTicketSuccessMsg).toContain('Đã sao chép');
      writeText.and.returnValue(Promise.reject(new Error('Denied')));
      await fixture.componentInstance.copyTicket([1, 2, 3, 4, 5, 6], 55);
      expect(fixture.componentInstance.copyTicketSuccessMsg).toContain('Chưa sao chép');
    } finally {
      if (original) Object.defineProperty(navigator, 'clipboard', original);
      else Reflect.deleteProperty(navigator, 'clipboard');
      fixture.destroy();
    }
  });
  it('passes original and Lab tickets for the current target to experimental deduplication', () => {
    TestBed.configureTestingModule({ providers: [{ provide: AnalyzeService, useValue: {} }] });
    const fixture = TestBed.createComponent(PredictionComponent);
    const component = fixture.componentInstance;
    component.payload = {
      category: 'MEGA',
      status: 'SUCCESS',
      targetDrawDate: '2026-11-04',
      primaryAlgorithmResult: { name: 'Primary', numbers: [1, 2, 3, 4, 5, 6] },
      secondaryAlgorithmResult: { name: 'Secondary', numbers: [7, 8, 9, 10, 11, 12] },
      combinedAlgorithmResult: { name: 'Combined', numbers: [1, 2, 7, 8, 9, 10] },
      workspace: {
        id: 'suggestion',
        category: 'MEGA',
        targetDate: '2026-11-04',
        createdAt: '2026-11-01T12:00:00Z',
        status: 'READY',
        expectedRevision: 0,
        primaryAlgorithm: 'temporal_learning',
        secondaryAlgorithm: 'bayesian_graph',
        primaryWeight: 0.5,
        keep: [],
        exclude: [],
        labStatus: 'READY',
        labPolicy: 'test',
        tickets: [{ id: 'lab', source: 'AI_LAB', label: 'Lab', numbers: [13, 14, 15, 16, 17, 18] }],
      },
    };
    const context = component.getMegaPortfolioContext();
    expect(context?.targetDate).toBe('2026-11-04');
    expect(context?.tickets.length).toBe(4);
    component.isSpinning = true;
    expect(component.getMegaPortfolioContext()).toBeNull();
    component.isSpinning = false;
    component.category = 'POWER';
    expect(component.getMegaPortfolioContext()).toBeNull();
    fixture.destroy();
  });
  it('does not request the legacy deviation matrix after predicting', () => {
    const legacy = jasmine.createSpy('getDeviationVariables');
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AnalyzeService,
          useValue: {
            getPrediction: () =>
              of({ status: 'SUCCESS', category: 'POWER', tickets: [[1, 2, 3, 4, 5, 6]] }),
            getDeviationVariables: legacy,
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(PredictionComponent);
    fixture.componentInstance.category = 'POWER';
    fixture.componentInstance.predict();
    expect(legacy).not.toHaveBeenCalled();
    expect(fixture.componentInstance.payload?.tickets).toEqual([[1, 2, 3, 4, 5, 6]]);
    fixture.destroy();
  });
  it('cancels an older request when changing lottery category', () => {
    const mega = new Subject<any>();
    const power = new Subject<any>();
    const api = {
      getPrediction: jasmine.createSpy().and.returnValues(mega, power),
      getDeviationVariables: () => of([]),
    };
    TestBed.configureTestingModule({ providers: [{ provide: AnalyzeService, useValue: api }] });
    const fixture = TestBed.createComponent(PredictionComponent);
    const component = fixture.componentInstance;
    component.category = 'MEGA';
    component.predict();
    component.category = 'POWER';
    component.predict();
    power.next({ status: 'SUCCESS', category: 'POWER', numbers: [9, 18, 27, 28, 31, 35] });
    mega.next({ status: 'SUCCESS', category: 'MEGA', numbers: [1, 2, 3, 4, 5, 6] });
    expect(component.payload?.category).toBe('POWER');
    expect(component.payload?.numbers).toEqual([9, 18, 27, 28, 31, 35]);
    fixture.destroy();
  });

  it('rejects a response whose category differs from the requested category', () => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AnalyzeService,
          useValue: {
            getPrediction: () => of({ status: 'SUCCESS', category: 'MEGA' }),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(PredictionComponent);
    fixture.componentInstance.category = 'POWER';
    fixture.componentInstance.predict();
    expect(fixture.componentInstance.payload).toBeNull();
    expect(fixture.componentInstance.errorMessage).toContain('không khớp');
    fixture.destroy();
  });
});
