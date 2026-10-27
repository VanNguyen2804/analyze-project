import { TestBed } from '@angular/core/testing';
import { ExpandedTicketsComponent } from './expanded-tickets.component';

describe('Expanded tickets', () => {
  it('opens on demand, limits actual tickets and requires opting into experimental tickets', () => {
    const fixture = TestBed.createComponent(ExpandedTicketsComponent);
    const tickets = [
      [1, 2, 3, 4, 5, 6],
      [7, 8, 9, 10, 11, 12],
      [13, 14, 15, 16, 17, 18],
      [19, 20, 21, 22, 23, 24],
      [25, 26, 27, 28, 29, 30],
      [31, 32, 33, 34, 35, 36],
    ];
    const experimental = [[40, 41, 42, 43, 44, 45]];
    fixture.componentRef.setInput('tickets', tickets);
    fixture.componentRef.setInput('experimental', experimental);
    fixture.componentRef.setInput('category', 'POWER');
    fixture.componentRef.setInput('specialNumber', 55);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const toggle = host.querySelector('.expand-button') as HTMLButtonElement;
    expect(host.querySelector('.expanded-body')).toBeNull();
    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(host.querySelectorAll('.expanded-ticket').length).toBe(5);
    const select = host.querySelector('select')!;
    expect(select.value).toBe('5');
    select.value = '3';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(host.querySelectorAll('.expanded-ticket').length).toBe(3);
    expect(fixture.componentInstance.displayed()).toEqual(tickets.slice(0, 3));
    const copied = jasmine.createSpy();
    fixture.componentInstance.copied.subscribe(copied);
    (host.querySelector('.ticket-label button') as HTMLButtonElement).click();
    expect(copied).toHaveBeenCalledWith({ numbers: tickets[0], special: 55 });
    (host.querySelector('input') as HTMLInputElement).click();
    fixture.detectChanges();
    expect(fixture.componentInstance.displayed()).toEqual(experimental);
    toggle.click();
    fixture.detectChanges();
    expect(host.querySelector('.expanded-body')).toBeNull();
    fixture.destroy();
  });
});
