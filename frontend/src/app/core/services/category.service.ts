import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { AnalyzeService } from './analyze.service';

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  private categorySubject = new BehaviorSubject<'MEGA' | 'POWER'>('MEGA');
  public category$: Observable<'MEGA' | 'POWER'> = this.categorySubject.asObservable();

  constructor(private analyzeService: AnalyzeService) {
    this.analyzeService.currentCategory$.subscribe(cat => {
      const normalized = (cat === 'POWER' ? 'POWER' : 'MEGA') as 'MEGA' | 'POWER';
      if (this.categorySubject.value !== normalized) {
        this.categorySubject.next(normalized);
      }
    });
  }

  get currentCategory(): 'MEGA' | 'POWER' {
    return this.categorySubject.value;
  }

  setCategory(cat: 'MEGA' | 'POWER'): void {
    if (this.categorySubject.value !== cat) {
      this.categorySubject.next(cat);
      this.analyzeService.setCategory(cat);
    }
  }
}

