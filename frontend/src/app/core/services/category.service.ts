import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  private categorySubject = new BehaviorSubject<'MEGA' | 'POWER'>('POWER');
  public category$: Observable<'MEGA' | 'POWER'> = this.categorySubject.asObservable();

  get currentCategory(): 'MEGA' | 'POWER' {
    return this.categorySubject.value;
  }

  setCategory(cat: 'MEGA' | 'POWER'): void {
    if (this.categorySubject.value !== cat) {
      this.categorySubject.next(cat);
    }
  }
}
