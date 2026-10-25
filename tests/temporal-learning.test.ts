import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import { learnTemporalRelationships, scheduledAfter, verifiedWinningTickets, LearningDraw } from '../src/temporal-learning';

function alternating(count = 30): LearningDraw[] {
  let date = '2026-08-01';
  return Array.from({ length: count }, (_, i) => {
    const result = { category: 'POWER', drawDate: date, numbers: i % 2 ? [20, 21, 22, 23, 24, 25] : [1, 2, 3, 4, 5, 6], specialNumber: 55 };
    date = scheduledAfter(date, 'POWER'); return result;
  });
}

test('learns repeatable transitions without inserting fixed seed numbers', () => {
  const history = alternating(), snapshot = JSON.stringify(history);
  const model = learnTemporalRelationships(history, 'POWER');
  assert.deepEqual(model.analysis.predictedNumbers, [1, 2, 3, 4, 5, 6]);
  assert.ok(model.analysis.patterns.find(p => p.id === 'lag1')!.weightPercent > model.analysis.patterns.find(p => p.id === 'frequency')!.weightPercent);
  assert.deepEqual(model, learnTemporalRelationships([...history].reverse(), 'POWER'));
  assert.equal(JSON.stringify(history), snapshot);
  assert.ok(model.scores.slice(1).every(p => Number.isFinite(p) && p > 0 && p < 1));
});

test('evaluation equals independently replayed predictions made before each target', () => {
  const history = alternating(18), result = learnTemporalRelationships(history, 'POWER');
  let total = 0, brier = 0;
  for (let t = 5; t < history.length; t++) {
    const prefix = learnTemporalRelationships(history.slice(0, t), 'POWER');
    total += prefix.analysis.predictedNumbers.filter(n => history[t].numbers.includes(n)).length;
    brier += prefix.scores.slice(1).reduce((sum, p, i) => sum + (p - (history[t].numbers.includes(i + 1) ? 1 : 0)) ** 2, 0) / 55;
  }
  assert.equal(result.analysis.evaluation.evaluatedDraws, history.length - 5);
  assert.equal(result.analysis.evaluation.averageMatched, total / (history.length - 5));
  assert.ok(Math.abs(result.analysis.evaluation.brierScore! - brier / (history.length - 5)) < 1e-12);
});

test('missing scheduled draws do not become invented consecutive transitions', () => {
  const history = alternating(3), model = learnTemporalRelationships([history[0], history[2]], 'POWER');
  assert.equal(model.analysis.skippedGapTransitions, 1);
  assert.equal(model.analysis.patterns.find(p => p.id === 'lag1')!.support, 0);
  assert.equal(model.analysis.evaluation.hit3RatePercent, null);
  assert.deepEqual(learnTemporalRelationships([], 'POWER').analysis.predictedNumbers, []);
});

test('invalid learning parameters are rejected in favor of finite defaults', () => {
  const model = learnTemporalRelationships(alternating(), 'POWER', { priorStrength: -1, learningRate: NaN, minHistory: -3 });
  assert.ok(model.scores.every(Number.isFinite));
  assert.equal(model.analysis.parameters.learningRate, 0.5);
});

test('user-supplied October 24 records verify the third ticket from actual intersections', () => {
  const history = JSON.parse(fs.readFileSync(new URL('../data/lottery_numbers.json', import.meta.url), 'utf8'));
  const tickets = JSON.parse(fs.readFileSync(new URL('../data/user_tickets.json', import.meta.url), 'utf8'));
  const verified = verifiedWinningTickets(history, tickets.map((t: any) => ({ ...t, matchedCount: 99 })), 'POWER').filter(t => t.drawDate === '2026-10-24');
  assert.equal(verified.length, 1);
  assert.deepEqual(verified[0].numbers, [6, 7, 11, 13, 18, 25]);
  assert.deepEqual(verified[0].matchedNumbers, [6, 7, 18]);
  assert.equal(verified[0].matchedCount, 3);
  assert.ok(verified[0].historicalLinks.some(link => link.drawDate === '2026-10-06' && link.sharedNumbers.length === 3));
  const model = learnTemporalRelationships(history, 'POWER');
  assert.equal(model.analysis.asOfDate, '2026-10-24');
});
