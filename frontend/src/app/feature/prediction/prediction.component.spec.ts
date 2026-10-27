import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { PredictionComponent } from './prediction.component';
import { AnalyzeService } from '@app/core/services/analyze.service';

describe('Prediction request category isolation', () => {
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
