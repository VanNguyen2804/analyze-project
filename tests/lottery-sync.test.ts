import test from 'node:test';
import assert from 'node:assert/strict';
import { LotterySync, latestExpected, mergeFeed, parseFeed, syncConfig, type StoredDraw } from '../src/lottery-sync';

const now = new Date('2026-10-25T03:00:00Z');
const mega = JSON.stringify({ date: '2026-10-23', id: '01579', result: [2, 5, 11, 22, 32, 40] });
const power = JSON.stringify({ date: '2026-10-24', id: '01415', result: [6, 7, 18, 41, 44, 50, 53] });

test('public NDJSON separates main/special and imports idempotently without overwriting conflicts', () => {
  const draws = parseFeed(`${power}\n${power}\n`, 'POWER', '2026-10-01', now);
  assert.equal(draws.length, 1);
  assert.deepEqual(draws[0].numbers, [6, 7, 18, 41, 44, 50]);
  assert.equal(draws[0].specialNumber, 53);
  const first = mergeFeed([], draws, 'https://example.com/power', now);
  assert.equal(first.inserted, 1);
  assert.match(first.records[0].note!, /01415/);
  const again = mergeFeed(first.records, draws, 'https://example.com/power', now);
  assert.equal(again.inserted, 0);
  assert.equal(again.unchanged, 1);
  const conflict = mergeFeed(first.records, [{ ...draws[0], specialNumber: 54 }], 'https://example.com/power', now);
  assert.equal(conflict.conflicts.length, 1);
  assert.equal(conflict.records[0].specialNumber, 53);
});

test('reject malformed, duplicate, out-of-range and conflicting feed results atomically', () => {
  for (const result of [[6, 7, 18, 41, 44, 50], [6, 7, 18, 41, 44, 50, 50], [0, 7, 18, 41, 44, 50, 53], [6.5, 7, 18, 41, 44, 50, 53]]) {
    assert.throws(() => parseFeed(JSON.stringify({ date: '2026-10-24', id: '01415', result }), 'POWER', '2026-10-01', now));
  }
  assert.throws(() => parseFeed(power + '\n' + power.replace('53', '54'), 'POWER', '2026-10-01', now));
  assert.throws(() => parseFeed('<html>error</html>', 'MEGA', '2026-10-01', now));
  assert.throws(() => parseFeed(mega.replace('2026-10-23', '2026-02-30'), 'MEGA', '2026-01-01', now));
});

test('Vietnam cutoff excludes unpublished/future draws and expected date handles weekend', () => {
  const today = mega.replace('2026-10-23', '2026-10-25').replace('01579', '01580');
  assert.equal(parseFeed(mega + '\n' + today, 'MEGA', '2026-10-01', now).length, 1);
  assert.equal(latestExpected('MEGA', now), '2026-10-23');
  const published = new Date('2026-10-25T11:30:00Z');
  assert.equal(parseFeed(mega + '\n' + today, 'MEGA', '2026-10-01', published).length, 2);
  assert.equal(latestExpected('MEGA', published), '2026-10-25');
  assert.equal(latestExpected('POWER', published), '2026-10-24');
});

test('source failures are isolated, retry works, stale feed reported, and failed writes never report success', async () => {
  let rows: StoredDraw[] = [], failMega = true, failWrite = false;
  const fetcher = (async (url: string | URL | Request) => {
    const isMega = String(url).includes('645');
    return new Response(isMega ? mega : power, { status: isMega && failMega ? 503 : 200 });
  }) as typeof fetch;
  const sync = new LotterySync(syncConfig({}), () => rows, next => {
    if (failWrite) throw new Error('Disk full');
    rows = next;
  }, fetcher);
  const first = await sync.run(now);
  assert.equal((first.lastRun.categories as any).MEGA.status, 'ERROR');
  assert.equal(rows.length, 1);
  failMega = false; failWrite = true;
  assert.equal(((await sync.run(now)).lastRun.categories as any).MEGA.status, 'ERROR');
  failWrite = false;
  const retry = await sync.run(now);
  assert.equal((retry.lastRun.categories as any).MEGA.inserted, 1);
  assert.equal((retry.lastRun.categories as any).POWER.unchanged, 1);
  assert.equal(rows.length, 2);
  const stale = await sync.run(new Date('2026-10-26T03:00:00Z'));
  assert.equal((stale.lastRun.categories as any).MEGA.status, 'STALE');
});

test('disable and overlapping runs do not fetch twice; configuration is bounded', async () => {
  let calls = 0;
  const fetcher = (async (url: any) => { calls++; return new Response(String(url).includes('645') ? mega : power); }) as typeof fetch;
  const disabled = new LotterySync(syncConfig({ LOTTERY_SYNC_ENABLED: 'false' }), () => [], () => {}, fetcher);
  await disabled.run(now);
  assert.equal(calls, 0);
  const enabled = new LotterySync(syncConfig({}), () => [], () => {}, fetcher);
  await Promise.all([enabled.run(now), enabled.run(now)]);
  assert.equal(calls, 2);
  assert.throws(() => syncConfig({ LOTTERY_SYNC_INTERVAL_SECONDS: '0' }));
  assert.throws(() => syncConfig({ LOTTERY_SYNC_MEGA_URL: 'http://example.com' }));
});
