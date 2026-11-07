# Stock and AI Lab performance — 7 November 2026

The public host returned HTTP 503 for the root, AI Lab and stock watchlist during the initial check; a later root request timed out. A final recheck returned HTTP 200 for all three endpoints (root about 1.0 seconds, watchlist 5.2 seconds, AI Lab 5.7 seconds), before any deployment of this work. These observations do not establish a hosting root cause. Render logs are not connected in this workspace, and these changes have not been deployed.

## Stock requests

The learning engine builds EMA/RSI features incrementally and normalizes training samples once per fit. It reuses small buffers throughout training. The model version, feature definitions, iteration order and decision thresholds remain unchanged. A pre-change full model result and exact feature-prefix comparisons guard the refactor, including no future-prefix leakage.

The [local measurement](reports/page-performance-2026-11-07.json) uses 1,250 synthetic daily candles: about 140 ms before versus 57 ms after, with current-thread allocated bytes falling from 246 MB to 0.47 MB. Full serialized model output matches exactly for this fixture. This single warmed measurement is illustrative, not a production latency guarantee or evidence of improved trading accuracy.

Analyses use per-symbol/mode locks instead of holding a global lock during provider calls. Duplicate work fails quickly with HTTP 503, `SERVICE_BUSY` and `Retry-After: 2`; other symbols and position reads remain available. Eight bounded training results reuse identical closed candle histories; freshness checks still run on refreshed snapshots. Watchlist quotes have a separate 30-day fetch and 30-second cache, rather than loading five years of training bars per symbol.

Provider calls allow two concurrent requests, with a 12-second overall load deadline, four seconds for quotes and at most five seconds per HTTP request. Failed refreshes preserve original data timestamps, mark verification false and respect retry cooldowns. Partial history is never cached as complete. Empty primary data skips unnecessary benchmark/daily fetches.

## AI Lab reads and reconciliation

Overview reads filter by category, validate each original snapshot before projecting it and retain only compact page/evaluation fields. PostgreSQL streams snapshots rather than retaining the full raw ledger, and reads only the latest settlement per forecast. Each category caches rendered rows and summaries for 30 seconds. Concurrent cold reads wait at most 250 ms for that category; MEGA and POWER reads are independent. Run completion invalidates the cache, including when capture is disabled or the run fails. GET never trains, captures or reconciles forecasts.

Evaluation indexes comparison controls instead of repeatedly scanning every forecast. Capture loads cheap IDs, reuses the base Logistic fit and reads only the required eligible feedback dates. Invalid newer snapshots do not displace valid older feedback. Reconciliation selects quality-filtered revisions once, skips unchanged forecasts before decoding large snapshots, and preserves append-only settlement events. Corrected and withdrawn results remain covered by real PostgreSQL tests. Original forecast snapshots and audit-ledger APIs remain intact.

The first cold overview still validates all historical snapshots for its category; this is not a pre-aggregated database read. Growing ledgers may eventually require persisted validated projections or incremental summaries. No production-scale AI Lab benchmark was available.

## Browser and runtime

Stock polling refreshes the selected analysis each minute and watchlist every five minutes; both pages pause automatic polling while hidden. Read timeouts release stalled loading states while preserving displayed data. Frontend budgets are 25 seconds for quotes, 40 seconds for analysis, 15 seconds for position/Lab reads and 60 seconds for explicit Lab runs.

`GET /api/health` is process liveness only: it does not access the database, market feed or model. Render Blueprint now uses this endpoint instead of `/api/numbers`. A manually configured Render service must also set its health-check path to `/api/health`. This endpoint does not certify database readiness. JSON/static compression and bounded Tomcat threads reduce resource pressure. Docker sets a 60% heap ceiling with headroom for native memory and two active JVM processors; these are defaults for the small deployment, not a diagnosis of the outage.

## Verification and rollout

Angular: 77 headless Edge tests, typecheck, formatter and production build. Java: final Maven install detected 168 tests: 161 passed, seven optional report/live/visual checks skipped, with no failures. This includes real PostgreSQL checks, concurrency tests, provider deadlines, cache invalidation and model regression. The earlier profiling run enabled the optional performance report. All 25 newly built Angular files were compared byte for byte with the final JAR. The Angular build retains an existing AI Lab stylesheet size warning (13.20 kB against the 10 kB warning budget); no stylesheet was changed in this work.

Deploy the reviewed build through the existing hosting workflow, verify `/api/health`, then check stock and Lab responses plus Render memory/CPU/restart logs. No hosting restart, production database write or deployment was performed from this workspace.
