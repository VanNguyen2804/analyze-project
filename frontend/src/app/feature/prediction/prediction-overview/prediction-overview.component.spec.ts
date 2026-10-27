import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PredictionOverviewComponent } from './prediction-overview.component';
import { AlgorithmResultItem } from '@app/core/models/prediction-payload.model';

describe('Prediction overview interactions', () => {
  function setup() {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(PredictionOverviewComponent);
    const primary: AlgorithmResultItem = {
      name: 'Mô hình 1',
      numbers: [1, 2, 3, 4, 5, 6],
      specialNumber: 55,
      details: [
        { number: 1, probabilityPercent: 99.9, frequency: 2, drawGap: 0 },
        { number: 2, probabilityPercent: 10, frequency: 8, drawGap: 3 },
      ],
    };
    fixture.componentRef.setInput('primary', primary);
    fixture.componentRef.setInput('secondary', {
      name: 'Mô hình 2',
      numbers: [2, 3, 7, 8, 9, 10],
      details: [],
    });
    fixture.componentRef.setInput('combined', {
      name: 'Kết hợp',
      numbers: [1, 2, 3, 7, 9, 11],
      details: [],
    });
    fixture.componentRef.setInput('category', 'POWER');
    fixture.componentRef.setInput('historyCount', 20);
    fixture.detectChanges();
    return { fixture, primary };
  }
  it('shows all three sets before the visual and copies the correct main and special numbers', () => {
    const { fixture, primary } = setup();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('.result-numbers span').length).toBe(18);
    expect(
      host
        .querySelector('.number-section')!
        .compareDocumentPosition(host.querySelector('.visual-section')!) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    const copied = jasmine.createSpy();
    fixture.componentInstance.copied.subscribe(copied);
    (host.querySelector('.copy-button') as HTMLButtonElement).click();
    expect(copied).toHaveBeenCalledWith({ numbers: primary.numbers, special: 55 });
    fixture.componentRef.setInput('category', 'MEGA');
    fixture.detectChanges();
    (host.querySelector('.copy-button') as HTMLButtonElement).click();
    expect(copied).toHaveBeenCalledWith({ numbers: primary.numbers, special: undefined });
    fixture.destroy();
  });
  it('switches real metrics, selects a number and handles models without detail data', () => {
    const { fixture } = setup();
    const host = fixture.nativeElement as HTMLElement;
    const component = fixture.componentInstance;
    expect(component.rows()[0].number).toBe(2);
    expect(component.width(2)).toBe(25);
    expect(host.textContent).not.toContain('99.9');
    expect(host.textContent).not.toContain('Điểm xếp hạng');
    (host.querySelectorAll('.metric-switch button')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.rows()[0].number).toBe(2);
    expect(component.width(3)).toBe(100);
    (host.querySelectorAll('.chart-row')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.focusedDetail()?.number).toBe(1);
    expect(host.querySelector('.number-detail')?.textContent).toContain('2');
    (host.querySelectorAll('.model-switch button')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(host.querySelector('.chart-empty')).not.toBeNull();
    expect(host.querySelector('.number-detail')).toBeNull();
    expect(component.shared()).toEqual([2, 3]);
    fixture.destroy();
  });
});
