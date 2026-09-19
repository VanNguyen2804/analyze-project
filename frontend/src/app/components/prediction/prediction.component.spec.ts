import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { PredictionComponent } from './prediction.component';
import { PredictionService } from '../../services/prediction.service';

describe('PredictionComponent', () => {
  let component: PredictionComponent;
  let fixture: ComponentFixture<PredictionComponent>;
  let predictionService: jasmine.SpyObj<PredictionService>;

  beforeEach(async () => {
    const spy = jasmine.createSpyObj('PredictionService', ['getPrediction']);

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      declarations: [PredictionComponent],
      providers: [
        { provide: PredictionService, useValue: spy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PredictionComponent);
    component = fixture.componentInstance;
    predictionService = TestBed.inject(PredictionService) as jasmine.SpyObj<PredictionService>;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should fetch predicted numbers on prediction click', (done) => {
    const mockNumbers = [5, 12, 18, 27, 33, 41];
    predictionService.getPrediction.and.returnValue(of(mockNumbers));

    component.onPredict();
    expect(component.isSpinning).toBeTrue();

    setTimeout(() => {
      expect(component.predictedNumbers).toEqual(mockNumbers);
      expect(component.isSpinning).toBeFalse();
      done();
    }, 1100);
  });
});
