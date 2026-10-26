import { TestBed } from '@angular/core/testing';
import { HyperparameterHistoryComponent } from './hyperparameter-history.component';

describe('Hyperparameter history', () => {
  it('owns the configuration dialog and emits reload/activation to its parent', () => {
    const fixture = TestBed.createComponent(HyperparameterHistoryComponent);
    fixture.componentRef.setInput('hyperparameterHistory', [
      { id: 1, version: 'v2', readmeContent: 'Current notes' },
      { id: 2, version: 'v1', readmeContent: 'Previous model notes' },
    ]);
    const component = fixture.componentInstance;
    const activate = jasmine.createSpy('activate');
    const reload = jasmine.createSpy('reload');
    component.activate.subscribe(activate);
    component.reload.subscribe(reload);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[title="Tải lại"]').click();
    fixture.nativeElement.querySelector('[title="Kích hoạt lại phiên bản này"]').click();
    expect(reload).toHaveBeenCalled();
    expect(activate).toHaveBeenCalledWith(2);
    fixture.nativeElement.querySelectorAll('[title="Xem nội dung tài liệu README"]')[1].click();
    fixture.detectChanges();
    const overlay = fixture.nativeElement.querySelector('.configuration-overlay');
    expect(overlay.textContent).toContain('Previous model notes');
    expect(getComputedStyle(overlay).zIndex).toBe('1060');
    overlay.querySelector('.btn-close').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.configuration-overlay')).toBeNull();
    fixture.destroy();
  });
});
