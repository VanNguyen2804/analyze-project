import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AnalyzeService } from './analyze.service';

describe('Official result persistence', () => {
  let api: AnalyzeService;
  let http: HttpTestingController;
  const saved = { id: 136, category: 'POWER', drawDate: '2026-11-17', numbers: [6,20,36,41,49,52], specialNumber: 34, quality: null };
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AnalyzeService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('reads the raw database list including pending verification', () => {
    let rows: any[] = [];
    api.getStoredHistory('POWER').subscribe(value => rows = value);
    const request = http.expectOne(r => r.url.endsWith('/api/numbers'));
    expect(request.request.params.get('category')).toBe('POWER');
    request.flush([saved]);
    expect(rows).toEqual([saved]);
  });
  it('only reports save success after reading back the same record', () => {
    const received = jasmine.createSpy();
    api.addOfficialResult(saved).subscribe(received);
    http.expectOne(r => r.method === 'POST' && r.url.endsWith('/api/numbers')).flush(saved);
    expect(received).not.toHaveBeenCalled();
    http.expectOne(r => r.method === 'GET' && r.params.get('date') === saved.drawDate).flush([saved]);
    expect(received).toHaveBeenCalledWith(saved);
  });
  it('reports an error when the saved record cannot be read back', () => {
    const success = jasmine.createSpy(), failure = jasmine.createSpy();
    api.addOfficialResult(saved).subscribe({ next: success, error: failure });
    http.expectOne(r => r.method === 'POST').flush(saved);
    http.expectOne(r => r.method === 'GET').flush([]);
    expect(success).not.toHaveBeenCalled();
    expect(failure).toHaveBeenCalled();
  });
  it('verifies edits through the same persistence endpoint', () => {
    const received = jasmine.createSpy();
    api.editOfficialResult(saved.id, saved).subscribe(received);
    http.expectOne(r => r.method === 'PUT' && r.url.endsWith('/api/numbers/136')).flush(saved);
    expect(received).not.toHaveBeenCalled();
    http.expectOne(r => r.method === 'GET').flush([saved]);
    expect(received).toHaveBeenCalled();
  });
});
