import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ForecastApiService } from '../../../core/services/forecast-api.service';
import { LabTicket } from '../../../core/models/forecast.model';
import { ExperimentalTicketsComponent } from './experimental-tickets.component';

describe('Experimental prediction tickets', () => {
  it('renders saved tickets with a separate experiment label and clears category requests', fakeAsync(() => {
    const mega = new Subject<LabTicket[]>();
    const power = new Subject<LabTicket[]>();
    const api = {
      labExperiments: jasmine
        .createSpy()
        .and.callFake((category: string) => (category === 'MEGA' ? mega : power)),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ForecastApiService, useValue: api }] });
    const fixture = TestBed.createComponent(ExperimentalTicketsComponent);
    fixture.detectChanges();
    tick(0);
    mega.next([
      {
        id: 'saved',
        model: 'logistic_concentrated',
        targetDate: '2026-11-18',
        createdAt: '2026-11-15T13:00:00Z',
        numbers: [1, 2, 3, 4, 5, 6],
      },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.experiment')).toBeTruthy();
    expect(fixture.nativeElement.querySelectorAll('.numbers span').length).toBe(6);
    expect(fixture.nativeElement.textContent).toContain('THỬ NGHIỆM');
    fixture.componentRef.setInput('category', 'POWER');
    fixture.detectChanges();
    tick(0);
    fixture.detectChanges();
    expect(fixture.componentInstance.rows()).toEqual([]);
    mega.next([
      {
        id: 'late',
        model: 'logistic_concentrated',
        targetDate: '2026-11-18',
        createdAt: '',
        numbers: [1, 2, 3, 4, 5, 6],
      },
    ]);
    expect(fixture.componentInstance.rows()).toEqual([]);
    power.next([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Chưa có bộ vé');
    fixture.destroy();
  }));
});
