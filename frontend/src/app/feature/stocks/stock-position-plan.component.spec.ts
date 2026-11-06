import { TestBed } from '@angular/core/testing';
import { StockPositionPlanComponent } from './stock-position-plan.component';
import { StockPositionFormComponent } from './stock-position-form.component';
import { StockPositionPlan, StockPlanStep } from '../../core/models/stock.model';
import { newStockPosition } from './stock-position-session';

function fixturePlan(): StockPositionPlan {
  const step: StockPlanStep = {
    id: 'TP1',
    side: 'SELL',
    title: 'Chốt lời 1',
    status: 'READY',
    triggerPrice: 33000,
    quantity: 300,
    cash: 9900000,
    averageAfter: 30000,
    quantityAfter: 700,
    pnlAtReference: 2100000,
    lossAtStop: 100000,
    breakEvenRisePercent: 0,
    profit: 900000,
    conditions: [{ label: 'Đạt mục tiêu', met: true }],
    blockers: [],
  };
  return {
    version: 'test',
    snapshotId: 'test',
    startedAt: '2026-11-03T00:00:00Z',
    status: 'TAKE_PROFIT',
    summary: 'Đạt mục tiêu',
    actionable: true,
    valuationPrice: 33000,
    valuationAsOf: 1793671200,
    valuationClosed: true,
    decisionPrice: 33000,
    decisionAsOf: 1793671200,
    referenceCost: 30000,
    averageCost: 30000,
    quantity: 1000,
    sellableQuantity: 400,
    capitalCommitted: 30000000,
    additionalSpent: 0,
    saleProceeds: 0,
    realizedPnl: 0,
    totalPnl: 3000000,
    remainingBudget: 1000000,
    alertPrice: 28500,
    originalStop: 27000,
    effectiveStop: 27000,
    observedPeak: null,
    steps: [step],
    cautions: [],
  };
}
describe('Position plan interaction', () => {
  it('requires explicit actual price and quantity; reaching a target never records a fill', () => {
    const fixture = TestBed.createComponent(StockPositionPlanComponent),
      c = fixture.componentInstance;
    fixture.componentRef.setInput('plan', fixturePlan());
    const record = jasmine.createSpy();
    c.recorded.subscribe(record);
    fixture.detectChanges();
    expect(record).not.toHaveBeenCalled();
    const step = fixturePlan().steps[0];
    c.open(step);
    expect(c.actualPrice).toBeNull();
    expect(c.actualQuantity).toBeNull();
    c.actualPrice = 33100;
    c.actualQuantity = 301;
    c.submit(step);
    expect(record).not.toHaveBeenCalled();
    c.actualQuantity = 200;
    c.submit(step);
    expect(record).toHaveBeenCalledOnceWith({ stepId: 'TP1', price: 33100, quantity: 200 });
    expect(fixturePlan().quantity).toBe(1000);
    fixture.destroy();
  });
  it('blocks repeat, pending, unknown sellable and stale-snapshot actions', () => {
    const fixture = TestBed.createComponent(StockPositionPlanComponent),
      c = fixture.componentInstance,
      step = fixturePlan().steps[0];
    for (const status of ['DONE', 'BLOCKED', 'WAIT_PREVIOUS', 'NEED_SELLABLE'])
      expect(c.canRecord({ ...step, status })).toBeFalse();
    fixture.componentRef.setInput('busy', true);
    expect(c.canRecord(step)).toBeFalse();
    fixture.destroy();
  });
  it('locks original cost and risk settings once executions exist while allowing sellable confirmation', () => {
    const fixture = TestBed.createComponent(StockPositionFormComponent),
      value = newStockPosition();
    value.purchasePrice = 30000;
    value.quantity = 1000;
    value.plan!.executions = [
      {
        stepId: 'AVG1',
        quantity: 100,
        price: 27000,
        executedAt: '2026-11-03T01:00:00Z',
        sellableBefore: null,
      },
    ];
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBeTrue();
    expect(
      fixture.nativeElement.querySelector('input[name="availableQuantity"]').disabled,
    ).toBeFalse();
    fixture.destroy();
  });
});
