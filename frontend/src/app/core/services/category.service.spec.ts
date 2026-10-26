import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { CategoryService } from './category.service';
import { AnalyzeService } from './analyze.service';

describe('Shared lottery category', () => {
  it('keeps the signal and existing observable consumers in sync without duplicate emissions', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient()] });
    const categories = TestBed.inject(CategoryService);
    const analyze = TestBed.inject(AnalyzeService);
    const changes: string[] = [];
    const subscription = analyze.currentCategory$.subscribe((category) => changes.push(category));

    analyze.setCategory('POWER');
    expect(categories.category()).toBe('POWER');
    expect(analyze.category()).toBe('POWER');
    categories.setCategory('power');
    expect(changes).toEqual(['MEGA', 'POWER']);
    categories.setCategory('MEGA');
    expect(analyze.category()).toBe('MEGA');
    expect(changes).toEqual(['MEGA', 'POWER', 'MEGA']);
    subscription.unsubscribe();
  });
});
