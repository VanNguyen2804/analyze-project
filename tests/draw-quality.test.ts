import test from 'node:test';
import assert from 'node:assert/strict';
import { eligibleDraw, eligibleHistory, reconcileQuality } from '../src/draw-quality';
import { mergeFeed, type StoredDraw, type FeedDraw } from '../src/lottery-sync';
import { learnTemporalRelationships } from '../src/temporal-learning';

const now = new Date('2026-10-25T12:00:00Z'), source = 'https://example.test/feed';
const feed: FeedDraw = { category:'POWER',drawDate:'2026-10-10',sourceDrawId:'01409',numbers:[14,17,21,23,24,39],specialNumber:30 };
const old: StoredDraw = { id:1,category:'POWER',drawDate:feed.drawDate,numbers:[4,5,6,29,32,44],specialNumber:53,createdAt:'2026-10-10',note:'Original note' };

test('reviewed correction preserves prior numbers, note, source evidence and is idempotent', () => {
  const result = reconcileQuality(old, feed, source, now);
  assert.deepEqual(result.numbers,feed.numbers);
  assert.ok(eligibleDraw(result));
  assert.deepEqual(result.quality!.audit[0].before.numbers,old.numbers);
  assert.equal(result.quality!.audit[0].before.note,'Original note');
  assert.match(result.quality!.audit[0].evidenceUrl,/xskt/);
  assert.equal(reconcileQuality(result,feed,source,now),result);
  assert.deepEqual(old.numbers,[4,5,6,29,32,44]);
});

test('missing special is filled only when the six main balls agree', () => {
  const incomplete = {...old,numbers:feed.numbers,specialNumber:undefined};
  const result = reconcileQuality(incomplete,feed,source,now);
  assert.equal(result.specialNumber,30);
  assert.equal(result.quality!.audit[0].reason,'FILLED_MISSING_SPECIAL');
  const unknown = reconcileQuality({...incomplete,numbers:[1,2,3,4,5,6]},feed,source,now);
  assert.equal(unknown.quality!.status,'QUARANTINED');
  assert.deepEqual(unknown.numbers,[1,2,3,4,5,6]);
});

test('unverified, quarantined and manually edited rows are excluded from learning', () => {
  const clean = reconcileQuality(old,feed,source,now);
  const edited = {...clean,numbers:[1,2,3,4,5,6]};
  const quarantined = reconcileQuality(clean,undefined,source,now);
  assert.equal(learnTemporalRelationships([old,edited,quarantined],'POWER').analysis.trainingDraws,0);
  assert.equal(learnTemporalRelationships([clean],'POWER').analysis.trainingDraws,1);
  assert.equal(eligibleDraw({...clean,specialNumber:99}),false);
});

test('duplicate revisions and absent in-window dates remain auditable and excluded', () => {
  const earlier = {...old,id:2,createdAt:'2026-10-09'};
  const absent = {...old,id:3,drawDate:'2026-10-09'};
  const firstFeed = {...feed,drawDate:'2026-10-08',sourceDrawId:'01408'};
  const result = mergeFeed([old,earlier,absent],[firstFeed,feed],source,now);
  assert.equal(result.corrected,1);
  assert.equal(result.quarantined.length,2);
  assert.equal(result.records.filter(eligibleDraw).length,2);
  const rerun = mergeFeed(result.records,[firstFeed,feed],source,now);
  assert.equal(rerun.updated,0);
  assert.equal(rerun.inserted,0);
  assert.equal(rerun.corrected,0);
});

test('a different incoming value cannot reuse an approved correction', () => {
  const different = {...feed,specialNumber:31};
  const result = reconcileQuality(old,different,source,now);
  assert.equal(result.quality!.status,'QUARANTINED');
  assert.equal(result.specialNumber,53);
});

test('a newer unverified revision cannot fall back to an older matched revision', () => {
  const matched = reconcileQuality(old,feed,source,now);
  const newer = {...old,id:2,createdAt:'2026-10-25'};
  assert.equal(eligibleHistory([matched,newer]).length,0);
  assert.equal(learnTemporalRelationships([matched,newer],'POWER').analysis.trainingDraws,0);
});
