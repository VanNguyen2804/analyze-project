import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { UserDeskComponent } from './user-desk.component';
import { WorkspaceApiService } from '@app/core/services/workspace-api.service';
import { WorkspaceSuggestion } from '@app/core/models/workspace.model';

describe('User prediction desk', () => {
  const suggestion = (category = 'MEGA'): WorkspaceSuggestion => ({
    id: `suggestion-${category}`,
    category,
    targetDate: '2099-01-02',
    createdAt: '2026-10-28T08:00:00Z',
    status: 'READY',
    expectedRevision: 0,
    primaryAlgorithm: 'temporal_learning',
    secondaryAlgorithm: 'bayesian_graph',
    primaryWeight: 0.5,
    keep: [],
    exclude: [],
    labStatus: 'READY',
    labPolicy: 'v1',
    tickets: [
      { id: 'primary', source: 'ALGORITHM', label: 'Nguồn gốc', numbers: [1, 2, 3, 4, 5, 6] },
      ...Array.from({ length: 5 }, (_, i) => ({
        id: `lab-${i}`,
        source: 'AI_LAB' as const,
        label: `Lab ${i}`,
        numbers: [10, 11, 12, 13, 14, 15 + i],
      })),
    ],
  });
  let api: {
    history: jasmine.Spy;
    commit: jasmine.Spy;
    combine: jasmine.Spy;
    reconcile: jasmine.Spy;
  };
  beforeEach(() => {
    localStorage.removeItem('prediction-user-draft-v1:MEGA');
    localStorage.removeItem('prediction-user-draft-v1:POWER');
    api = {
      history: jasmine.createSpy().and.returnValue(of({ batches: [] })),
      commit: jasmine.createSpy().and.returnValue(throwError(() => ({ status: 503 }))),
      combine: jasmine.createSpy(),
      reconcile: jasmine.createSpy(),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: WorkspaceApiService, useValue: api }],
    });
  });
  function mount() {
    const fixture = TestBed.createComponent(UserDeskComponent);
    fixture.componentRef.setInput('suggestion', suggestion());
    fixture.detectChanges();
    return fixture;
  }
  it('keeps source identity after editing and reuses the request key on network retry', () => {
    const fixture = mount();
    const c = fixture.componentInstance;
    c.add(suggestion().tickets[0]);
    c.edit(c.rows()[0].key, '1 2 3 4 5 22');
    c.commit();
    c.commit();
    expect(api.commit.calls.count()).toBe(2);
    const first = api.commit.calls.argsFor(0)[0],
      second = api.commit.calls.argsFor(1)[0];
    expect(first.requestKey).toBe(second.requestKey);
    expect(first.tickets).toEqual([{ sourceId: 'primary', numbers: [1, 2, 3, 4, 5, 22] }]);
    c.edit(c.rows()[0].key, '1 2 3 4 5 23');
    c.commit();
    expect(api.commit.calls.mostRecent().args[0].requestKey).not.toBe(first.requestKey);
    fixture.destroy();
  });
  it('rejects invalid and duplicate tickets before sending a commit', () => {
    const fixture = mount(),
      c = fixture.componentInstance;
    c.add();
    c.edit(c.rows()[0].key, '0 0 0 0 0 0');
    c.commit();
    expect(api.commit).not.toHaveBeenCalled();
    c.edit(c.rows()[0].key, '1 2 3 4 5 6');
    c.add(suggestion().tickets[0]);
    c.commit();
    expect(api.commit).not.toHaveBeenCalled();
    expect(c.error()).toContain('trùng');
    fixture.destroy();
  });
  it('shows 3 to 5 lab options and records the displayed count with the selected tickets', () => {
    const fixture = mount(),
      c = fixture.componentInstance;
    c.labCount.set(3);
    fixture.detectChanges();
    expect(c.labTickets().length).toBe(3);
    c.add(c.labTickets()[0]);
    c.commit();
    expect(api.commit.calls.mostRecent().args[0].labCount).toBe(3);
    fixture.destroy();
  });
  it('restores draft numbers and their original suggestion after remount', () => {
    let fixture = mount();
    fixture.componentInstance.add(suggestion().tickets[0]);
    fixture.componentInstance.edit(fixture.componentInstance.rows()[0].key, '1 2 3 4 5 22');
    fixture.destroy();
    fixture = TestBed.createComponent(UserDeskComponent);
    fixture.componentRef.setInput('suggestion', { ...suggestion(), id: 'new-suggestion' });
    fixture.detectChanges();
    expect(fixture.componentInstance.view()?.id).toBe('suggestion-MEGA');
    expect(fixture.componentInstance.rows()[0].text).toBe('1 2 3 4 5 22');
    fixture.destroy();
  });
  it('cancels mutation response when changing category', () => {
    const pending = new Subject<any>();
    api.commit.and.returnValue(pending);
    const fixture = mount(),
      c = fixture.componentInstance;
    c.add(suggestion().tickets[0]);
    c.commit();
    fixture.componentRef.setInput('suggestion', suggestion('POWER'));
    fixture.detectChanges();
    pending.next({ revision: 99, tickets: [{}], targetDate: '2099-01-02' });
    expect(c.view()?.category).toBe('POWER');
    expect(c.view()?.expectedRevision).toBe(0);
    expect(c.rows()).toEqual([]);
    fixture.destroy();
  });
  it('can choose, edit and submit a ticket through the rendered form without mobile overflow', async () => {
    const fixture = mount();
    fixture.nativeElement.style.width = '390px';
    const choose = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((b) => b.textContent?.includes('Chọn / sửa vé'))!;
    choose.click();
    fixture.detectChanges();
    const row = fixture.componentInstance.rows()[0];
    const input = fixture.nativeElement.querySelector(`[id="${row.key}"]`) as HTMLInputElement;
    input.value = '1 2 3 4 5 22';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.commit') as HTMLButtonElement).click();
    expect(api.commit.calls.mostRecent().args[0].tickets[0].numbers).toEqual([1, 2, 3, 4, 5, 22]);
    expect(fixture.nativeElement.scrollWidth).toBeLessThanOrEqual(392);
    fixture.destroy();
  });
  it('keeps the newest suggestions available while restoring a previous draft', () => {
    let fixture = mount();
    fixture.componentInstance.add(suggestion().tickets[0]);
    fixture.destroy();
    fixture = TestBed.createComponent(UserDeskComponent);
    fixture.componentRef.setInput('suggestion', { ...suggestion(), id: 'newest' });
    fixture.detectChanges();
    expect(fixture.componentInstance.view()?.id).toBe('suggestion-MEGA');
    fixture.componentInstance.useLatest();
    expect(fixture.componentInstance.view()?.id).toBe('newest');
    expect(fixture.componentInstance.rows()).toEqual([]);
    fixture.destroy();
  });
  it('shows the concentration of selected tickets before committing and rejects negative input', () => {
    const fixture = mount();
    const c = fixture.componentInstance;
    c.add();
    c.edit(c.rows()[0].key, '7 9 18 27 31 40');
    c.add();
    c.edit(c.rows()[1].key, '7 14 18 27 31 40');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('7 số khác nhau');
    expect(fixture.nativeElement.textContent).toContain('5/6 số');
    c.edit(c.rows()[1].key, '7 14 18 27 31 -40');
    c.commit();
    expect(api.commit).not.toHaveBeenCalled();
    fixture.destroy();
  });
});
