import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { HeaderComponent } from './share/components/header/header.component';
import { LeftMenuComponent } from './share/components/left-menu/left-menu.component';

@Component({ selector: 'app-header', template: '' })
class HeaderStub {}
@Component({ selector: 'app-left-menu', template: '' })
class LeftMenuStub {}

describe('AppComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([])],
    }).overrideComponent(AppComponent, {
      remove: { imports: [HeaderComponent, LeftMenuComponent] },
      add: { imports: [HeaderStub, LeftMenuStub] },
    });
  });
  it('renders the application shell', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.title).toBe('Analyze Project');
    expect(fixture.nativeElement.querySelector('main router-outlet')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-header')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-left-menu')).not.toBeNull();
  });
});
