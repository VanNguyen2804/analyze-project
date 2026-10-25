import fs from 'node:fs';
import path from 'node:path';

export type Category = 'MEGA' | 'POWER';
export interface FeedDraw {
  category: Category;
  drawDate: string;
  sourceDrawId: string;
  numbers: number[];
  specialNumber?: number;
}
export interface StoredDraw {
  id: number;
  category: Category;
  drawDate: string;
  numbers: number[];
  specialNumber?: number;
  createdAt: string;
  note?: string;
}
export interface SyncConfig {
  enabled: boolean;
  intervalSeconds: number;
  startupDelaySeconds: number;
  lookbackDays: number;
  timeoutSeconds: number;
  urls: Record<Category, string>;
}
const MAX_BYTES = 5_000_000;
export const SYNC_TIMEZONE = 'Asia/Ho_Chi_Minh';

export function syncConfig(env: NodeJS.ProcessEnv = process.env): SyncConfig {
  const integer = (key: string, fallback: number, min: number, max: number) => {
    const value = env[key] === undefined ? fallback : Number(env[key]);
    if (!Number.isInteger(value) || value < min || value > max) throw new Error(`${key} must be between ${min} and ${max}`);
    return value;
  };
  if (env.LOTTERY_SYNC_ENABLED !== undefined && !['true', 'false'].includes(env.LOTTERY_SYNC_ENABLED)) {
    throw new Error('LOTTERY_SYNC_ENABLED must be true or false');
  }
  const urls = {
    MEGA: env.LOTTERY_SYNC_MEGA_URL || 'https://raw.githubusercontent.com/vietvudanh/vietlott-data/main/data/power645.jsonl',
    POWER: env.LOTTERY_SYNC_POWER_URL || 'https://raw.githubusercontent.com/vietvudanh/vietlott-data/main/data/power655.jsonl',
  };
  for (const url of Object.values(urls)) {
    const parsed = new URL(url);
    if (parsed.protocol !== 'https:' || parsed.username || parsed.password) throw new Error('Feed URL must use HTTPS without credentials');
  }
  return {
    enabled: env.LOTTERY_SYNC_ENABLED !== 'false', urls,
    intervalSeconds: integer('LOTTERY_SYNC_INTERVAL_SECONDS', 900, 60, 86400),
    startupDelaySeconds: integer('LOTTERY_SYNC_STARTUP_DELAY_SECONDS', 10, 0, 3600),
    lookbackDays: integer('LOTTERY_SYNC_LOOKBACK_DAYS', 90, 1, 7300),
    timeoutSeconds: integer('LOTTERY_SYNC_TIMEOUT_SECONDS', 20, 1, 120),
  };
}

function localTime(now: Date) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: SYNC_TIMEZONE, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(now);
  const p = Object.fromEntries(parts.map(x => [x.type, x.value]));
  return { date: `${p.year}-${p.month}-${p.day}`, afterDraw: Number(p.hour) * 60 + Number(p.minute) >= 18 * 60 + 30 };
}

export function dateOffset(date: string, days: number): string {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}

export function latestExpected(category: Category, now: Date): string {
  const local = localTime(now);
  let date = local.afterDraw ? local.date : dateOffset(local.date, -1);
  const days = category === 'POWER' ? [2, 4, 6] : [0, 3, 5];
  while (!days.includes(new Date(`${date}T00:00:00Z`).getUTCDay())) date = dateOffset(date, -1);
  return date;
}

export function parseFeed(body: string, category: Category, fromDate: string, now = new Date()): FeedDraw[] {
  if (!body.trim() || Buffer.byteLength(body) > MAX_BYTES) throw new Error('Empty or oversized feed');
  const local = localTime(now);
  const draws = new Map<string, FeedDraw>();
  for (const line of body.split(/\r?\n/)) {
    if (!line.trim()) continue;
    const row = JSON.parse(line);
    if (typeof row.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(row.date)
      || !Number.isFinite(Date.parse(row.date)) || new Date(row.date).toISOString().slice(0, 10) !== row.date) throw new Error('Invalid draw date');
    if (row.date < fromDate || row.date > local.date || (row.date === local.date && !local.afterDraw)) continue;
    const power = category === 'POWER';
    if (typeof row.id !== 'string' || !/^\d{1,12}$/.test(row.id)
      || !Array.isArray(row.result) || row.result.length !== (power ? 7 : 6)
      || row.result.some((n: unknown) => !Number.isInteger(n) || Number(n) < 1 || Number(n) > (power ? 55 : 45))
      || new Set(row.result).size !== row.result.length) throw new Error(`Invalid result for ${category} ${row.date}`);
    const draw: FeedDraw = { category, drawDate: row.date, sourceDrawId: row.id,
      numbers: row.result.slice(0, 6).sort((a: number, b: number) => a - b),
      ...(power ? { specialNumber: row.result[6] } : {}),
    };
    const previous = draws.get(draw.drawDate);
    if (previous && JSON.stringify(previous) !== JSON.stringify(draw)) throw new Error(`Conflicting source rows on ${draw.drawDate}`);
    draws.set(draw.drawDate, draw);
  }
  if (!draws.size) throw new Error('Feed has no completed draws in the requested window');
  return [...draws.values()].sort((a, b) => a.drawDate.localeCompare(b.drawDate));
}

export async function fetchFeed(url: string, timeoutSeconds: number, fetcher = fetch): Promise<string> {
  const response = await fetcher(url, { signal: AbortSignal.timeout(timeoutSeconds * 1000), redirect: 'error',
    headers: { Accept: 'application/x-ndjson, text/plain', 'User-Agent': 'analyze-project-result-sync/1.0' } });
  if (!response.ok || !response.body) throw new Error(`Feed returned HTTP ${response.status}`);
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BYTES) { await reader.cancel(); throw new Error('Feed exceeds size limit'); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  return Buffer.concat(chunks).toString('utf8');
}

export function mergeFeed(existing: StoredDraw[], incoming: FeedDraw[], sourceUrl: string, now = new Date()) {
  const records = [...existing];
  const current = new Map<string, StoredDraw>();
  [...existing].sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id)
    .forEach(row => current.set(`${row.category}:${row.drawDate}`, row));
  let id = Math.max(0, ...existing.map(row => row.id)) + 1;
  let inserted = 0, unchanged = 0;
  const conflicts: { date: string; stored: number[]; storedSpecial?: number; incoming: number[]; incomingSpecial?: number }[] = [];
  for (const draw of incoming) {
    const key = `${draw.category}:${draw.drawDate}`;
    const prior = current.get(key);
    if (prior) {
      if ([...prior.numbers].sort((a, b) => a - b).join(',') === draw.numbers.join(',')
        && (prior.specialNumber ?? null) === (draw.specialNumber ?? null)) unchanged++;
      else conflicts.push({ date: draw.drawDate, stored: prior.numbers, storedSpecial: prior.specialNumber,
        incoming: draw.numbers, incomingSpecial: draw.specialNumber });
    } else {
      const record = { ...draw, id: id++, createdAt: now.toISOString(),
        note: `Imported from ${sourceUrl} | draw #${draw.sourceDrawId} | community mirror of vietlott.vn` };
      records.push(record); current.set(key, record); inserted++;
    }
  }
  return { records, inserted, unchanged, conflicts };
}

/** Publish the new in-memory snapshot only after its atomic disk replacement succeeds. */
export function writeResults(file: string, records: StoredDraw[]): void {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const temporary = `${file}.${process.pid}.tmp`;
  try {
    fs.writeFileSync(temporary, JSON.stringify(records, null, 2) + '\n', 'utf8');
    fs.renameSync(temporary, file);
  } finally {
    if (fs.existsSync(temporary)) fs.unlinkSync(temporary);
  }
}

export class LotterySync {
  private lastRun: Record<string, unknown> = { status: 'NOT_RUN' };
  private running = false;
  constructor(readonly config: SyncConfig, private read: () => StoredDraw[],
    private commit: (rows: StoredDraw[]) => void, private fetcher = fetch) {}

  status() {
    return { enabled: this.config.enabled, intervalSeconds: this.config.intervalSeconds,
      timezone: SYNC_TIMEZONE, lookbackDays: this.config.lookbackDays, lastRun: this.lastRun };
  }

  async run(now = new Date()) {
    if (!this.config.enabled || this.running) return this.status();
    this.running = true;
    try {
      const fromDate = dateOffset(localTime(now).date, -this.config.lookbackDays);
      const categories: Record<string, unknown> = {};
      for (const category of ['MEGA', 'POWER'] as const) {
        const sourceUrl = this.config.urls[category];
        try {
          const body = await fetchFeed(sourceUrl, this.config.timeoutSeconds, this.fetcher);
          const draws = parseFeed(body, category, fromDate, now);
          const { records, ...imported } = mergeFeed(this.read(), draws, sourceUrl, now);
          if (imported.inserted) this.commit(records);
          const latestSourceDate = draws[draws.length - 1].drawDate;
          const expectedLatestDate = latestExpected(category, now);
          const sourceStale = latestSourceDate < expectedLatestDate;
          categories[category] = { sourceUrl, status: imported.conflicts.length ? 'CONFLICT' : sourceStale ? 'STALE' : 'SUCCESS',
            latestSourceDate, expectedLatestDate, sourceStale, ...imported };
        } catch (error) {
          categories[category] = { sourceUrl, status: 'ERROR', error: error instanceof Error ? error.message : String(error) };
        }
      }
      this.lastRun = { checkedAt: now.toISOString(), fromDate, categories };
      return this.status();
    } finally { this.running = false; }
  }

  start() {
    if (!this.config.enabled) return () => {};
    let stopped = false;
    const tick = async () => {
      try { console.info('Lottery sync:', JSON.stringify(await this.run())); }
      catch (error) { console.warn('Lottery sync failed; retry on next scheduled run', error); }
      finally { if (!stopped) { timer = setTimeout(tick, this.config.intervalSeconds * 1000); timer.unref(); } }
    };
    let timer = setTimeout(tick, this.config.startupDelaySeconds * 1000);
    timer.unref();
    return () => { stopped = true; clearTimeout(timer); };
  }
}
