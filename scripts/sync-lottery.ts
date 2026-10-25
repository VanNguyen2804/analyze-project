import fs from 'node:fs';
import path from 'node:path';
import { LotterySync, syncConfig, writeResults, type StoredDraw } from '../src/lottery-sync';

// Run while the Node server is stopped; the JSON backend supports one writer process.
const args = process.argv.slice(2);
const file = path.resolve(args[0] || 'data/lottery_numbers.json');
let records: StoredDraw[] = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : [];
const sync = new LotterySync(syncConfig(), () => records, rows => { writeResults(file, rows); records = rows; });
const report = await sync.run();
console.log(JSON.stringify(report, null, 2));
if (args[1]) fs.writeFileSync(path.resolve(args[1]), JSON.stringify(report, null, 2) + '\n', 'utf8');
const categories = report.lastRun.categories as Record<string, { status: string }> | undefined;
if (!report.enabled || !categories || Object.values(categories).some(c => c.status === 'ERROR')) process.exitCode = 1;
