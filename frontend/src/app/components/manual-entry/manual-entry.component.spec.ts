import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { ManualEntryComponent } from './manual-entry.component';
import { LotteryService } from '../../services/lottery.service';

describe('ManualEntryComponent', () => {
  let component: ManualEntryComponent;
  let fixture: ComponentFixture<ManualEntryComponent>;
  let lotteryService: jasmine.SpyObj<LotteryService>;

  beforeEach(async () => {
    const spy = jasmine.createSpyObj('LotteryService', ['getAll', 'save', 'delete']);

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, FormsModule],
      declarations: [ManualEntryComponent],
      providers: [
        { provide: LotteryService, useValue: spy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ManualEntryComponent);
    component = fixture.componentInstance;
    lotteryService = TestBed.inject(LotteryService) as jasmine.SpyObj<LotteryService>;

    lotteryService.getAll.and.returnValue(of([]));
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should toggle numbers in grid', () => {
    component.toggleGridNumber(7);
    expect(component.selectedSet.has(7)).toBeTrue();
    expect(component.selectedSet.size).toBe(1);

    component.toggleGridNumber(7);
    expect(component.selectedSet.has(7)).toBeFalse();
    expect(component.selectedSet.size).toBe(0);
  });
});
