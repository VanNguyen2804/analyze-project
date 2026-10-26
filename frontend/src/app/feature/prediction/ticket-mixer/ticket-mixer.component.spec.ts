import { TestBed } from '@angular/core/testing';
import { TicketMixerComponent } from './ticket-mixer.component';

describe('Ticket mixer', () => {
  it('keeps selection within six numbers and emits a sorted copy without mutating suggestions', () => {
    const fixture = TestBed.createComponent(TicketMixerComponent);
    const primary = [8, 7, 6, 5, 4, 3];
    fixture.componentRef.setInput('primaryNumbers', primary);
    fixture.componentRef.setInput('numbers', [1, 2, 3, 4, 5, 6, 7, 8]);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.fillMixerAuto();
    expect(component.customMixerNumbers).toEqual([1, 2, 3, 4, 5, 6]);
    component.toggleMixerNumber(7);
    expect(component.customMixerNumbers.length).toBe(6);
    component.toggleMixerNumber(1);
    component.toggleMixerNumber(8);
    const copied = jasmine.createSpy('copied');
    component.copied.subscribe(copied);
    component.copyMixerTicket();
    expect(copied).toHaveBeenCalledWith([2, 3, 4, 5, 6, 8]);
    expect(copied.calls.mostRecent().args[0]).not.toBe(component.customMixerNumbers);
    expect(primary).toEqual([8, 7, 6, 5, 4, 3]);
    fixture.detectChanges();
    const selected = fixture.nativeElement.querySelector('.mixer-selected-ball');
    expect(getComputedStyle(selected).width).toBe('42px');
    fixture.destroy();
  });
});
