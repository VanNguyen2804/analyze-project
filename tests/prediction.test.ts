import { verifiedFixture } from './quality-fixture';
import assert from 'node:assert/strict';
import test from 'node:test';
import { fuseRankings, nextDraw, seededRandom, summarizePatterns } from '../src/algorithm-support';

process.env.NODE_ENV = 'test';
const { analyzeAndPredict } = await import('../server');

function history(category: 'POWER' | 'MEGA', offset = 0) {
  const max = category === 'POWER' ? 55 : 45;
  return Array.from({ length: 15 }, (_, i) => ({
    id: i + 1, category, drawDate: `2026-09-${String(i + 1).padStart(2, '0')}`,
    numbers: Array.from({ length: 6 }, (_, j) => (i * 3 + j * 7 + offset) % max + 1),
    createdAt: '2026-09-01T00:00:00Z',
  })).map(verifiedFixture);
}

test('rank fusion treats both models equally and removes duplicates', () => {
  const a = [1, 2, 3, 4, 5, 6, 7], b = [7, 6, 5, 4, 3, 2, 1];
  assert.deepEqual(fuseRankings(a, b), fuseRankings(b, a));
  assert.deepEqual(fuseRankings([1, 1, 2], [2, 3], 3), [1, 2, 3]);
});

test('draw scheduling handles the cutoff, year rollover, and a future stored draw', () => {
  assert.equal(nextDraw('MEGA', new Date('2026-10-25T10:59:59Z')).drawDate, '2026-10-25');
  assert.equal(nextDraw('MEGA', new Date('2026-10-25T11:00:00Z')).drawDate, '2026-10-28');
  assert.equal(nextDraw('POWER', new Date('2026-12-31T11:00:00Z')).drawDate, '2027-01-02');
  assert.equal(nextDraw('POWER', new Date('2026-10-25T10:00:00Z'), '2026-11-10').drawDate, '2026-11-12');
  assert.throws(() => nextDraw('MEGA', new Date(), '', '2026-02-30'));
});

test('simulation PRNG is reproducible and remains in range', () => {
  const a = seededRandom('history'), b = seededRandom('history');
  for (let i = 0; i < 100; i++) {
    const value = a();
    assert.equal(value, b());
    assert.ok(value >= 0 && value < 1);
  }
});

test('pattern percentages are measured from the supplied sample', () => {
  const result = summarizePatterns([[1, 2, 3, 4, 5, 6], [11, 13, 15, 17, 19, 21]], 45);
  assert.equal(result.evaluatedDraws, 2);
  assert.equal(result.parityPattern.percentage, 50);
  assert.equal(result.consecutivePairPattern.percentage, 50);
  assert.equal(result.tailModuloPattern.percentage, 50);
  assert.equal(summarizePatterns([], 45).amplitude1Pattern.percentage, 0);
});

test('empty and invalid histories do not produce fabricated predictions', () => {
  assert.equal(analyzeAndPredict('MEGA', 'bayesian_graph', []).status, 'ERROR');
  assert.deepEqual(analyzeAndPredict('POWER', 'bayesian_graph', []).tickets, []);
  const invalid = [{ ...history('MEGA')[0], numbers: [1, 1, 2, 3, 4, 99] }];
  assert.equal(analyzeAndPredict('MEGA', 'bayesian_graph', invalid).totalDrawsAnalyzed, 0);
});

for (const category of ['POWER', 'MEGA'] as const) {
  test(`${category}: model outputs follow history and the secondary algorithm selection`, () => {
    const draws = history(category);
    const original = JSON.stringify(draws);
    const first = analyzeAndPredict(category, 'bayesian_graph', draws, 'deep_stacking');
    const repeated = analyzeAndPredict(category, 'bayesian_graph', [...draws].reverse(), 'deep_stacking');
    const secondary = analyzeAndPredict(category, 'deep_stacking', draws, 'SKIP_RECURSION');
    assert.equal(first.status, 'SUCCESS');
    assert.deepEqual(first.tickets, repeated.tickets);
    assert.deepEqual(first.allNumberScores, repeated.allNumberScores);
    assert.deepEqual(first.secondaryAlgorithmResult.numbers, secondary.pureNumbers);
    assert.ok(first.combinedAlgorithmResult);
    for (const result of [first.primaryAlgorithmResult, first.secondaryAlgorithmResult, first.combinedAlgorithmResult]) {
      assert.equal(result.numbers.length, 6);
      assert.equal(new Set(result.numbers).size, 6);
      assert.ok(result.numbers.every((n: number) => n >= 1 && n <= (category === 'POWER' ? 55 : 45)));
      assert.equal(result.accuracyScore, null);
      if (result.specialNumber != null) assert.ok(!result.numbers.includes(result.specialNumber));
    }
    assert.equal(first.baseDrawDate, '2026-09-15');
    assert.deepEqual(first.recommendations.megaDrawReconciliation.officialWinningNumbers, draws.at(-1)!.numbers);
    assert.equal(first.recommendations.megaDrawReconciliation.upgradedAiCoverage, null);
    for (const ticket of first.recommendations.goldenTicketsRecommendation) {
      if (ticket.specialNumber != null) assert.ok(!ticket.numbers.includes(ticket.specialNumber));
    }
    const changed = analyzeAndPredict(category, 'bayesian_graph', history(category, 4), 'SKIP_RECURSION');
    assert.notDeepEqual(first.pureNumbers, changed.pureNumbers);
    assert.equal(JSON.stringify(draws), original);
    assert.equal(analyzeAndPredict(category, 'bayesian_graph', [...draws, draws[0]], 'SKIP_RECURSION').totalDrawsAnalyzed, draws.length);
  });
}

test('all advertised Node algorithms are deterministic', () => {
  for (const algorithm of ['temporal_learning', 'deep_stacking', 'bayesian_graph', 'xgboost', 'poisson_gap', 'delta_wheeling', 'montecarlo', 'markov']) {
    const a = analyzeAndPredict('MEGA', algorithm, history('MEGA'), 'SKIP_RECURSION');
    const b = analyzeAndPredict('MEGA', algorithm, history('MEGA'), 'SKIP_RECURSION');
    assert.deepEqual(a.allNumberScores, b.allNumberScores, algorithm);
    assert.ok(a.allNumberScores!.every(n => Number.isFinite(n.probabilityPercent)), algorithm);
  }
});

test('the temporal API ticket is the learned selection rather than a legacy balancing fallback', () => {
  for (const category of ['POWER', 'MEGA'] as const) {
    const prediction = analyzeAndPredict(category, 'temporal_learning', history(category), 'bayesian_graph');
    assert.equal(prediction.algorithm, 'temporal_learning');
    assert.deepEqual(prediction.pureNumbers, prediction.relationshipLearning?.predictedNumbers);
    assert.equal(prediction.modelVersion, 'temporal-links-v1');
    assert.equal(prediction.primaryAlgorithmResult.numbers.length, 6);
  }
});
