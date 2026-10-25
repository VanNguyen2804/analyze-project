/** Equal-weight reciprocal rank fusion; result order is numeric for ticket display. */
export function fuseRankings(first: number[], second: number[], count = 6): number[] {
  const scores = new Map<number, number>();
  for (const ranking of [first, second]) {
    [...new Set(ranking)].forEach((number, rank) => {
      scores.set(number, (scores.get(number) ?? 0) + 1 / (rank + 1));
    });
  }
  return [...scores.keys()]
    .sort((a, b) => scores.get(b)! - scores.get(a)! || a - b)
    .slice(0, count).sort((a, b) => a - b);
}

/** Reproducible simulation without introducing random jitter into model scores. */
export function seededRandom(seed: string): () => number {
  let state = 2166136261;
  for (let i = 0; i < seed.length; i++) state = Math.imul(state ^ seed.charCodeAt(i), 16777619);
  return () => {
    state += 0x6d2b79f5;
    let value = Math.imul(state ^ (state >>> 15), 1 | state);
    value ^= value + Math.imul(value ^ (value >>> 7), 61 | value);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

export function specialFor(preferred: number | null | undefined, ticket: number[], ranking: number[]): number | undefined {
  if (preferred == null) return undefined;
  return !ticket.includes(preferred) ? preferred : ranking.find(n => !ticket.includes(n));
}

export function summarizePatterns(draws: number[][], max: number) {
  const percent = (count: number, total = draws.length) => total ? Math.round(count / total * 1000) / 10 : 0;
  const pattern = (rule: string, predicate: (numbers: number[]) => boolean) => {
    const count = draws.filter(predicate).length;
    return { rule, occurrencesCount: count, percentage: percent(count), insight: `${count}/${draws.length} stored draws` };
  };
  let driftCount = 0;
  for (let i = 1; i < draws.length; i++) {
    const previous = draws[i - 1];
    if (draws[i].filter(n => !previous.includes(n) && previous.some(p => Math.abs(p - n) === 1)).length >= 2) driftCount++;
  }
  const sumMin = Math.round(3 * (max + 1) - max), sumMax = Math.round(3 * (max + 1) + max);
  return {
    evaluatedDraws: draws.length,
    amplitude1Pattern: { rule: 'At least two neighbor shifts', occurrencesCount: driftCount,
      percentage: percent(driftCount, Math.max(0, draws.length - 1)), insight: `${driftCount}/${Math.max(0, draws.length - 1)} draw transitions` },
    parityPattern: { ...pattern('Two to four even numbers', n => { const even = n.filter(v => v % 2 === 0).length; return even >= 2 && even <= 4; }), balanceRatio: '2:4 / 3:3 / 4:2' },
    zoneDistributionPattern: pattern('All three equal-width zones represented', n => new Set(n.map(v => Math.min(2, Math.floor((v - 1) * 3 / max)))).size === 3),
    tailModuloPattern: pattern('At least one shared units digit', n => new Set(n.map(v => v % 10)).size < n.length),
    consecutivePairPattern: pattern('At least one consecutive pair', n => n.some(v => n.includes(v + 1))),
    sumRangePattern: { ...pattern('Sum within one ball-range of the uniform mean', n => { const sum = n.reduce((a, b) => a + b, 0); return sum >= sumMin && sum <= sumMax; }), optimalRange: `${sumMin} - ${sumMax}` },
  };
}

export function nextDraw(category: 'POWER' | 'MEGA', now = new Date(), baseDate = '', fromDate?: string) {
  // UTC accessors on a shifted instant keep this independent of the server timezone.
  const vietnam = new Date(now.getTime() + 7 * 60 * 60 * 1000);
  const today = vietnam.toISOString().slice(0, 10);
  const startDate = fromDate ?? today;
  let candidate = new Date(`${startDate}T00:00:00Z`);
  if (!Number.isFinite(candidate.getTime()) || candidate.toISOString().slice(0, 10) !== startDate) {
    throw new Error('Invalid draw date');
  }
  if (baseDate && baseDate > startDate) candidate = new Date(`${baseDate}T00:00:00Z`);
  const days = category === 'POWER' ? [2, 4, 6] : [0, 3, 5];
  const names = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
  for (;;) {
    const date = candidate.toISOString().slice(0, 10);
    const beforeCutoff = date !== today || vietnam.getUTCHours() < 18;
    if (days.includes(candidate.getUTCDay()) && date > baseDate && beforeCutoff) {
      return {
        drawDate: date,
        dayName: names[candidate.getUTCDay()],
        drawDateFormatted: date.split('-').reverse().join('/'),
        isToday: date === today,
        baseDrawDate: baseDate,
        baseDrawFormatted: baseDate ? baseDate.split('-').reverse().join('/') : '',
      };
    }
    candidate.setUTCDate(candidate.getUTCDate() + 1);
  }
}
