import { TestBed } from '@angular/core/testing';
import { LabProgressComponent } from './lab-progress.component';
import { LabProgress } from '../../../core/models/forecast.model';

const progress: LabProgress = {
  evaluationVersion: 'lab-progress-v1',
  evaluatedTickets: 10,
  totalMatched: 9,
  bestTicketMatched: 2,
  averageMatchedPerTicket: 0.9,
  frequencyAverageMatched: 0.5,
  randomAverageMatched: 0.5,
  recent30AverageMatched: 0.9,
  previous30AverageMatched: null,
  matchHistogram: [2, 7, 1, 0, 0, 0, 0],
  levels: [
    {
      threshold: 1,
      ticketHits: 8,
      ticketPercent: 80,
      drawHits: 2,
      drawPercent: 100,
      frequencyTicketPercent: 50,
      randomTicketPercent: 50,
    },
    {
      threshold: 2,
      ticketHits: 1,
      ticketPercent: 10,
      drawHits: 1,
      drawPercent: 50,
      frequencyTicketPercent: 0,
      randomTicketPercent: 0,
    },
    {
      threshold: 3,
      ticketHits: 0,
      ticketPercent: 0,
      drawHits: 0,
      drawPercent: 0,
      frequencyTicketPercent: 0,
      randomTicketPercent: 0,
    },
  ],
  timeline: [
    { targetDate: '2026-10-25', matched: 4, cumulativeMatched: 4, averageMatchedPerTicket: 0.8 },
    { targetDate: '2026-10-28', matched: 5, cumulativeMatched: 9, averageMatchedPerTicket: 0.9 },
  ],
};

describe('AI Lab gradual progress', () => {
  it('starts at one match and switches comparisons to two without turning nine cumulative matches into a winning ticket', () => {
    const fixture = TestBed.createComponent(LabProgressComponent);
    fixture.componentRef.setInput('progress', progress);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('.headline strong')?.textContent).toContain('0.90');
    expect(root.querySelector('.cumulative')?.textContent).toContain('9 lượt số khớp');
    expect(root.querySelector('.comparison b')?.getAttribute('style')).toContain('80%');
    (root.querySelectorAll('.levels button')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(root.querySelector('.comparison b')?.getAttribute('style')).toContain('10%');
    expect(root.querySelectorAll('.levels button')[1].getAttribute('aria-pressed')).toBe('true');
    expect(root.textContent).toContain('không phải một vé trúng ghép từ nhiều kỳ');
    expect(root.textContent).toContain('2/6 số');
    fixture.destroy();
  });
  it('keeps percentages and averages unknown with no settled data', () => {
    const fixture = TestBed.createComponent(LabProgressComponent);
    fixture.componentRef.setInput('progress', {
      ...progress,
      evaluatedTickets: 0,
      totalMatched: 0,
      bestTicketMatched: 0,
      averageMatchedPerTicket: null,
      timeline: [],
      levels: progress.levels.map((l) => ({
        ...l,
        ticketHits: 0,
        ticketPercent: null,
        drawHits: 0,
        drawPercent: null,
        frequencyTicketPercent: null,
        randomTicketPercent: null,
      })),
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.headline strong').textContent).toContain(
      'Chưa có',
    );
    expect(fixture.nativeElement.querySelector('.comparison').textContent).toContain('Chưa có');
    fixture.destroy();
  });
});
