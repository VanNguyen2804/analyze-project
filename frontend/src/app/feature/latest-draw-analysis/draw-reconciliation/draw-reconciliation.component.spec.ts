import { TestBed } from '@angular/core/testing';
import { DrawReconciliationComponent } from './draw-reconciliation.component';

describe('Read-only draw evaluation', () => {
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
