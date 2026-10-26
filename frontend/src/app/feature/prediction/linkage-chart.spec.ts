import { buildLinkageChartData, LinkageHistoryRecord } from './linkage-chart';

describe('Linkage chart projection', () => {
  const records: LinkageHistoryRecord[] = [
    { id: 1, drawDate: '2026-10-21', numbers: [1, 2, 3, 4, 5, 6] },
    { id: 2, drawDate: '2026-10-23', numbers: [2, 4, 6, 8, 10, 12] },
  ];

  it('does not invent chart data for an empty history', () => {
    expect(buildLinkageChartData('MEGA', [])).toBeNull();
  });

  it('sorts the timeline without mutating input and preserves the measured distributions', () => {
    const reversed = [...records].reverse();
    const chart = buildLinkageChartData('MEGA', reversed)!;
    expect(reversed[0].id).toBe(2);
    expect(chart).toEqual(buildLinkageChartData('MEGA', records)!);
    expect(chart.totalDraws).toBe(2);
    expect(chart.recentDraws.map((draw) => draw.drawDate)).toEqual(['2026-10-21', '2026-10-23']);
    expect(chart.sumDistributions.reduce((total, item) => total + item.count, 0)).toBe(2);
    expect(chart.gapPressureList.find((item) => item.number === 2)?.frequency).toBe(2);
    expect(
      chart.transitionLinks.some((link) => link.fromNumber === 2 && link.toNumber === 2),
    ).toBeTrue();
  });
});
