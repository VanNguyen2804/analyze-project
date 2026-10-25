import { eligibleHistory, type QualityDraw } from './draw-quality';
export interface LearningDraw extends QualityDraw {
  drawDate: string;
  numbers: number[];
  category?: string;
  specialNumber?: number | null;
  createdAt?: string;
  id?: number;
}

export interface LearningOptions { priorStrength?: number; learningRate?: number; minHistory?: number }

const FEATURES = ['frequency', 'lag1', 'lag2', 'repeat', 'neighbor', 'same_tail', 'special_to_main'];
const LABELS = ['Tần suất nền', 'Chuyển tiếp một kỳ', 'Chuyển tiếp hai kỳ', 'Lặp số', 'Lân cận ±1', 'Cùng đuôi', 'Số phụ sang số chính'];

export function scheduledAfter(date: string, category: string): string {
  const value = new Date(`${date}T00:00:00Z`);
  const days = category === 'POWER' ? [2, 4, 6] : [0, 3, 5];
  do { value.setUTCDate(value.getUTCDate() + 1); } while (!days.includes(value.getUTCDay()));
  return value.toISOString().slice(0, 10);
}

export function normalizeLearningHistory(history: LearningDraw[], category: string): LearningDraw[] {
  const max = category === 'POWER' ? 55 : 45;
  const dates = new Set<string>();
  return eligibleHistory(history).filter(r => {
    const date = new Date(`${r.drawDate}T00:00:00Z`);
    return (!r.category || r.category === category) && Number.isFinite(date.getTime())
      && date.toISOString().slice(0, 10) === r.drawDate && r.numbers?.length === 6
      && new Set(r.numbers).size === 6 && r.numbers.every(n => Number.isInteger(n) && n >= 1 && n <= max);
  }).sort((a, b) => b.drawDate.localeCompare(a.drawDate) || (b.createdAt ?? '').localeCompare(a.createdAt ?? '') || (b.id ?? 0) - (a.id ?? 0))
    .filter(r => { if (dates.has(r.drawDate)) return false; dates.add(r.drawDate); return true; })
    .reverse().map(r => ({ ...r, numbers: [...r.numbers].sort((a, b) => a - b) }));
}

/** Online expert weighting. Every validation forecast is made before its target is observed. */
export function learnTemporalRelationships(history: LearningDraw[], category: string, options: LearningOptions = {}) {
  const draws = normalizeLearningHistory(history, category), max = category === 'POWER' ? 55 : 45, base = 6 / max;
  const finite = (value: unknown, fallback: number, min: number, limit: number) =>
    typeof value === 'number' && Number.isFinite(value) && value >= min && value <= limit ? value : fallback;
  const prior = finite(options.priorStrength, max / 6, 0.01, 10000);
  const rate = finite(options.learningRate, 0.5, 0, 10);
  const minHistory = Math.round(finite(options.minHistory, 5, 1, 1000));
  const frequency = Array(max + 1).fill(0) as number[];
  const counts = Array.from({ length: 2 }, () => Array.from({ length: max + 1 }, () => Array(max + 1).fill(0) as number[]));
  const exposures = Array.from({ length: 2 }, () => Array(max + 1).fill(0) as number[]);
  const successes = Array.from({ length: 4 }, () => [0, 0]);
  const trials = Array.from({ length: 4 }, () => [0, 0]);
  let weights = FEATURES.map(() => 1 / FEATURES.length), seen = 0, evaluated = 0, skipped = 0, matched = 0, baselineMatched = 0, candidateMatched = 0, fallbackDraws = 0, hit3 = 0, modelBrier = 0, baselineBrier = 0;
  const rank = (scores: number[]) => Array.from({ length: max }, (_, i) => i + 1).sort((a, b) => scores[b] - scores[a] || a - b);
  const normalize = (scores: number[]) => { const sum = scores.slice(1).reduce((a, b) => a + b, 0); return scores.map((p, i) => i === 0 ? 0 : Math.min(0.999, Math.max(0.001, p * 6 / sum))); };
  const flags = (n: number, previous?: LearningDraw) => [previous?.numbers.includes(n) ?? false,
    previous?.numbers.some(p => Math.abs(p - n) === 1) ?? false,
    previous?.numbers.some(p => p !== n && p % 10 === n % 10) ?? false,
    previous?.specialNumber === n];
  const adjacent = (i: number) => i > 0 && scheduledAfter(draws[i - 1].drawDate, category) === draws[i].drawDate;
  const experts = (previous?: LearningDraw, older?: LearningDraw) => {
    const result = FEATURES.map(() => Array(max + 1).fill(0) as number[]);
    for (let n = 1; n <= max; n++) {
      const marginal = (frequency[n] + prior * base) / (seen + prior);
      result[0][n] = marginal;
      for (let lag = 0; lag < 2; lag++) {
        const source = lag === 0 ? previous : older;
        result[lag + 1][n] = source ? source.numbers.reduce((sum, seed) => sum
          + (counts[lag][seed][n] + prior * marginal) / (exposures[lag][seed] + prior), 0) / 6 : marginal;
      }
      flags(n, previous).forEach((flag, feature) => {
        const state = flag ? 1 : 0;
        result[feature + 3][n] = feature === 3 && !previous?.specialNumber ? marginal
          : (successes[feature][state] + prior * marginal) / (trials[feature][state] + prior);
      });
    }
    return result.map(normalize);
  };
  const mix = (models: number[][]) => normalize(Array.from({ length: max + 1 }, (_, n) => models.reduce((sum, scores, i) => sum + weights[i] * scores[n], 0)));
  for (let t = 0; t < draws.length; t++) {
    const draw = draws[t], actual = new Set(draw.numbers), connected = adjacent(t);
    const models = experts(connected ? draws[t - 1] : undefined, connected && adjacent(t - 1) ? draws[t - 2] : undefined);
    if (seen >= minHistory && connected) {
      const candidate = mix(models), useBaseline = evaluated > 0 && candidateMatched < baselineMatched;
      const forecast = useBaseline ? models[0] : candidate, top = rank(forecast).slice(0, 6), reference = rank(models[0]).slice(0, 6);
      const hits = top.filter(n => actual.has(n)).length;
      candidateMatched += rank(candidate).slice(0, 6).filter(n => actual.has(n)).length;
      if (useBaseline) fallbackDraws++;
      evaluated++; matched += hits; baselineMatched += reference.filter(n => actual.has(n)).length; if (hits >= 3) hit3++;
      const losses = models.map(scores => scores.slice(1).reduce((sum, p, i) => sum + (p - (actual.has(i + 1) ? 1 : 0)) ** 2, 0) / max);
      modelBrier += forecast.slice(1).reduce((sum, p, i) => sum + (p - (actual.has(i + 1) ? 1 : 0)) ** 2, 0) / max;
      baselineBrier += losses[0];
      const next = weights.map((w, i) => w * Math.exp(-rate * max * losses[i])), total = next.reduce((a, b) => a + b, 0);
      weights = next.map(w => w / total);
    }
    if (t > 0 && !connected) skipped++;
    if (connected) {
      for (let lag = 0; lag < 2; lag++) {
        if (lag === 1 && !adjacent(t - 1)) continue;
        for (const source of draws[t - lag - 1].numbers) {
          exposures[lag][source]++;
          for (const target of draw.numbers) counts[lag][source][target]++;
        }
      }
      for (let n = 1; n <= max; n++) flags(n, draws[t - 1]).forEach((flag, feature) => {
        if (feature === 3 && !draws[t - 1].specialNumber) return;
        const state = flag ? 1 : 0; trials[feature][state]++; if (actual.has(n)) successes[feature][state]++;
      });
    }
    for (const n of draw.numbers) frequency[n]++;
    seen++;
  }
  const last = draws.at(-1), finalExperts = experts(last, adjacent(draws.length - 1) ? draws.at(-2) : undefined);
  const useBaseline = evaluated > 0 && candidateMatched < baselineMatched;
  const scores = useBaseline ? finalExperts[0] : mix(finalExperts);
  const topTransitions: Array<{fromNumber: number; toNumber: number; support: number; occurrences: number; conditionalPercent: number}> = [];
  for (const source of last?.numbers ?? []) for (let target = 1; target <= max; target++) {
    if (counts[0][source][target] > 0) topTransitions.push({ fromNumber: source, toNumber: target, support: exposures[0][source], occurrences: counts[0][source][target],
      conditionalPercent: 100 * (counts[0][source][target] + prior * (frequency[target] + prior * base) / (seen + prior)) / (exposures[0][source] + prior) });
  }
  topTransitions.sort((a, b) => b.conditionalPercent - a.conditionalPercent || b.support - a.support || a.fromNumber - b.fromNumber || a.toNumber - b.toNumber);
  return {
    scores,
    analysis: {
      modelVersion: 'temporal-links-v1', category, asOfDate: last?.drawDate ?? null, trainingDraws: seen,
      forecastPolicy: useBaseline ? 'frequency_fallback' : 'learned_mixture',
      skippedGapTransitions: skipped, parameters: { priorStrength: prior, learningRate: rate, minHistory },
      patterns: FEATURES.map((id, i) => ({ id, name: LABELS[i], weightPercent: weights[i] * 100,
        support: i < 3 ? (i === 0 ? seen : exposures[i - 1].reduce((a, b) => a + b, 0) / 6) : trials[i - 3][1],
        occurrences: i < 3 ? null : successes[i - 3][1] })),
      topTransitions: topTransitions.slice(0, 12), predictedNumbers: seen ? rank(scores).slice(0, 6).sort((a, b) => a - b) : [],
      evaluation: { method: 'prequential', evaluatedDraws: evaluated, minHistory,
        averageMatched: evaluated ? matched / evaluated : null, baselineAverageMatched: evaluated ? baselineMatched / evaluated : null,
        candidateAverageMatched: evaluated ? candidateMatched / evaluated : null, baselineFallbackDraws: fallbackDraws,
        hit3RatePercent: evaluated ? hit3 / evaluated * 100 : null,
        brierScore: evaluated ? modelBrier / evaluated : null, baselineBrierScore: evaluated ? baselineBrier / evaluated : null },
    },
  };
}

export function verifiedWinningTickets(history: LearningDraw[], tickets: Array<{ id?: number; drawDate: string; numbers: number[]; category?: string }>, category: string) {
  const draws = new Map(normalizeLearningHistory(history, category).map(draw => [draw.drawDate, draw]));
  return tickets.filter(ticket => (!ticket.category || ticket.category === category) && ticket.numbers?.length === 6 && new Set(ticket.numbers).size === 6)
    .flatMap(ticket => {
      const draw = draws.get(ticket.drawDate); if (!draw) return [];
      const matchedNumbers = ticket.numbers.filter(n => draw.numbers.includes(n));
      const historicalLinks = [...draws.values()].filter(previous => previous.drawDate < draw.drawDate)
        .map(previous => ({ drawDate: previous.drawDate, sharedNumbers: matchedNumbers.filter(n => previous.numbers.includes(n)),
          daysApart: Math.round((Date.parse(draw.drawDate) - Date.parse(previous.drawDate)) / 86400000) }))
        .filter(link => link.sharedNumbers.length >= 2)
        .sort((a, b) => b.sharedNumbers.length - a.sharedNumbers.length || b.drawDate.localeCompare(a.drawDate)).slice(0, 5);
      return matchedNumbers.length >= 3 ? [{ id: ticket.id, drawDate: ticket.drawDate, numbers: ticket.numbers, matchedNumbers, matchedCount: matchedNumbers.length, historicalLinks }] : [];
    }).sort((a, b) => b.drawDate.localeCompare(a.drawDate) || b.matchedCount - a.matchedCount).slice(0, 10);
}
