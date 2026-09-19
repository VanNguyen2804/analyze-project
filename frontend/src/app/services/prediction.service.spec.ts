import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { PredictionService } from './prediction.service';

describe('PredictionService', () => {
  let service: PredictionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [PredictionService]
    });
    service = TestBed.inject(PredictionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return predicted numbers from API', () => {
    const mockNumbers = [3, 11, 24, 29, 38, 42];

    service.getPrediction().subscribe(numbers => {
      expect(numbers.length).toBe(6);
      expect(numbers).toEqual(mockNumbers);
    });

    const req = httpMock.expectOne('/api/analyze/predict');
    expect(req.request.method).toBe('GET');
    req.flush(mockNumbers);
  });
});
