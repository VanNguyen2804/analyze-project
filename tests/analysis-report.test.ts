import assert from 'node:assert/strict';
import test from 'node:test';
import { explainDraw, summarizeBacktest } from '../src/analysis-report';

test('backtest summary measures actual six-number tickets and skips unavailable forecasts', () => {
  const draws = [
    {drawDate:'2026-10-23',aiPrediction:{predicted6Numbers:[1,2,3,4,5,6],matchedIn6Count:3}},
    {drawDate:'2026-10-21',aiPrediction:{predicted6Numbers:[1,2,3,4,5,6],matchedIn6Count:0}},
    {drawDate:'2026-10-18',aiPrediction:{predicted6Numbers:[],matchedIn6Count:0}}
  ];
  const summary = summarizeBacktest(draws,39,'temporal_learning','MEGA');
  assert.equal(summary.evaluatedDrawsCount,2);
  assert.equal(summary.averageMatched,1.5);
  assert.equal(summary.hit3RatePercent,50);
  assert.equal(summary.totalHistoricalDraws,39);
  assert.equal(summary.fromDate,'2026-10-18');
  assert.equal(summarizeBacktest([],0,'temporal_learning','MEGA').hit3RatePercent,null);
  draws[0].aiPrediction.matchedIn6Count=0;
  assert.equal(summarizeBacktest(draws,39,'temporal_learning','MEGA').hit3RatePercent,0);
});

test('draw explanations use actual prior frequency and unknown gap when never observed', () => {
  const prior=[{category:'POWER',drawDate:'2026-10-22',numbers:[1,2,3,4,5,6],specialNumber:7}];
  const draw={category:'POWER',drawDate:'2026-10-24',numbers:[1,2,8,9,10,11],specialNumber:7};
  const explanation=explainDraw(draw,prior,[1,2,3,4,5,6]);
  assert.equal(explanation.whyWinningBallsAppeared.find(n=>n.number===1)?.frequency,1);
  assert.equal(explanation.whyWinningBallsAppeared.find(n=>n.number===1)?.drawGap,0);
  assert.equal(explanation.whyWinningBallsAppeared.find(n=>n.number===8)?.drawGap,null);
  assert.equal(explanation.whyWinningBallsAppeared.find(n=>n.isSpecial)?.frequency,1);
  assert.equal(explanation.whyAlgorithmMissed.missedFactors.length,4);
});
