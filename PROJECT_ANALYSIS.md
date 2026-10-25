# Project analysis and algorithm changes

Reviewed 2026-10-25.

## Architecture

There are two separate application stacks:

| Stack | Entrypoints | Storage |
| --- | --- | --- |
| Local Node/Vite | `server.ts`, `src/main.ts` | JSON files in `data/` |
| Docker deployment | Angular `frontend/`, Spring Boot `backend/` | PostgreSQL, Flyway migrations |

The prediction implementations have diverged. Running the root npm scripts does not exercise Spring's algorithm. The Node server also prefers built Angular assets when they exist.

## Changes implemented

- Replaced Spring's fixed primary/secondary tickets and ensemble seed numbers with rankings calculated from the supplied history. The requested secondary algorithm now actually runs.
- Added equal-weight reciprocal rank fusion in both stacks: each number receives `1 / rank` from each model, with a deterministic numeric tie-break. The six highest totals form the combined ticket. Both APIs now include the previously omitted combined result.
- Reused exponentially weighted frequency totals for Bayesian successes/failures instead of scanning every draw again for every candidate. The configured momentum decay now controls this weighting.
- Removed random jitter from Node scores. Monte Carlo sampling uses a PRNG seeded from category, algorithm, and history, so identical inputs reproduce the same output.
- Reject invalid main-number histories and count a draw date once, preferring the latest stored revision. Explicitly empty custom histories return an error with empty predictions instead of silently training on live data.
- Removed Spring's full-dataset fallback for the earliest backtest draw and fixed both backtests to evaluate the actual six-number primary ticket rather than the first six numerically sorted pool entries.
- Removed preset accuracy and hit rates from prediction cards and catalogs. Unmeasured accuracy is `null`/`N/A`. Legacy `probabilityPercent` fields still carry heuristic scores for compatibility; they are not calibrated probabilities.
- Replaced Spring's fixed recommendations, base dates, special numbers, and claimed perfect coverage. Removed Node's fabricated focus/reality success reports. Latest-draw reconciliation no longer treats a training draw as a successful forecast.
- Calculated Node pattern percentages from the supplied draw sample. Replaced random Spring official-draw feature values with observed transition/co-occurrence statistics and a deterministic gap score.
- Added timezone-independent draw scheduling helpers, including the Vietnam 18:00 cutoff, year rollover, and a database whose latest draw is ahead of the current date.
- Removed Angular's invented secondary/combined ticket and transformation fallbacks. Missing scores now display as unavailable.
- Node's listening port now honors `PORT` and validates its range.

Game limits, six-number ticket sizes, draw weekdays, and the existing cutoff are domain rules; they remain explicit. This change does not establish improved lottery prediction accuracy or train a new XGBoost model.

## Validation

The current implementation also includes online relationship learning and the user-supplied POWER draw for 2026-10-24. See the detailed section below.

```powershell
# Root application
npm ci
npm test
npm run lint
npm run build

# Spring unit/regression tests; no database required
cd backend
mvn clean install

# Angular; use a Node version supported by its declared dependencies
cd ../frontend
npm install
npm run build -- --configuration development
```

Regression coverage includes changed/reordered/empty histories, duplicate and invalid draws, secondary algorithm selection, symmetric rank fusion, special-number collisions, deterministic sampling, measured pattern percentages, category-specific parameter fallback, the earliest backtest draw, and date boundaries.

The installed default Node 20.9 cannot run the declared Angular 22 toolchain. Angular production `npm run build` was run through a temporary Node 24 runtime. Existing Angular template/deprecation warnings remain. Unit tests use in-memory fixtures and mocked repositories. The packaged application was additionally started against temporary PostgreSQL with Flyway migrations and checked through its HTTP APIs; this is not a production deployment or independent verification of the supplied results.

## POWER 2026-10-24 and online relationship learning

The user supplied the main numbers **06, 07, 18, 41, 44, 50**, special number **53**, and three played tickets. Recomputed intersections are respectively **1**, **0**, and **3** main numbers. The third ticket **06, 07, 11, 13, 18, 25** matches **06, 07, 18**. These three numbers also occur together in the stored 2026-10-06 result; the pair **07, 18** also occurs on 2026-10-03, 2026-09-19, and 2026-08-25. These are historical observations, not proof of a predictable future outcome.

- Added the supplied result and tickets to both JSON resource sets with provenance and actual import times. The proposed `V10__add_user_supplied_power_2026_10_24.sql` migration was subsequently removed at the user's request because the database already contains these records. Existing database rows and Flyway schema history were not modified. New draw results are handled by the result scheduler.
- Added `temporal_learning` in both backends. It sequentially fits seven experts: frequency, lag-one and lag-two transitions, repeats, neighbors, shared units digits, and special-to-main migration. Conditional counts are shrunk toward the marginal frequency using a configurable prior. Exponential expert weights update from Brier loss after each evaluated result is observed.
- Skips transition training and evaluation across missing scheduled draws. Every reported test forecast is generated before its target result is incorporated; tests independently replay prefixes to verify this property.
- Selects the frequency baseline when the learned mixture has matched fewer main numbers in the evaluations available at prediction time. It continues learning rather than advertising improvement unsupported by the sample.
- The local JSON POWER history has 25 draws, four missing-draw transitions, and 16 evaluated forecasts. The policy averages 0.9375 main-number matches per six-number ticket versus 1.0 for the frequency baseline; the raw learned mixture averages 0.75. This sample does **not** establish an improvement in ticket accuracy. Metrics are history-specific.
- Existing Flyway seed data omits 2026-10-13, 2026-10-15, and 2026-10-17 POWER draws present in the JSON resources. The earlier temporary PostgreSQL validation had 22 POWER draws and 13 evaluated forecasts after applying the then-proposed V10, with different metrics/predictions. These are historical measurements from before V10 removal and scheduler imports, not the current clean-install state. Existing historical data was not silently replaced to force parity. The report records both histories.
- Added `/api/analyze/relationship-learning?category=POWER&date=2026-10-24` to audit a specific date. Missing dates return `DRAW_NOT_FOUND`; later draws are excluded from that report.
- Both APIs use the catalog in `backend/src/main/resources/data/algorithm_catalog.json`. Angular and the root UI fetch it instead of embedding ranks, model lists, or invented accuracy rates.
- Angular renders observed patterns, learned weights, sample counts, before-update evaluation, baseline selection, verified winning tickets, and their actual historical overlaps. Removed fixed high-number boosters, dated narratives, fixed percentages and special-number/date fallbacks from this flow.
- A Maven profile uses the existing Angular build for local packaging; Docker's injected static assets remain supported when that external build directory is absent. Verified all nine packaged frontend files against the fresh Angular output.

Detailed data, model parameters, metrics, predictions and source differences: [reports/power-2026-10-24.json](reports/power-2026-10-24.json). The displayed next POWER target from this snapshot is 2026-10-27. Future updates should regenerate the report when its input changes.

Historical validation before V10 removal: root tests/type-check/build; Angular production `npm run build`; backend `mvn clean install`; V10 applied twice on temporary PostgreSQL and its conflicting-result rejection checked with rollback; full Flyway V1–V10 startup and catalog/learning/prediction/static HTTP responses checked against the packaged JAR. Temporary services were stopped after verification. The saved reports preserve those historical runs; the current migration set ends at V9.

## Automatic Vietlott result synchronization (2026-10-25)

The public `vietvudanh/vietlott-data` repository provides MIT-licensed NDJSON feeds without credentials. Live retrieval verified MEGA #01579 on 2026-10-23: **02, 05, 11, 22, 32, 40** and POWER #01415 on 2026-10-24: **06, 07, 18, 41, 44, 50**, special **53**. The direct Vietlott website returned HTTP 403 in this environment. The configured provider is a community mirror, not an official supported API; freshness depends on its daily collection jobs.

- New Spring `sync/` package and Node `src/lottery-sync.ts` poll both feeds after startup and every 900 seconds after completion, configurable by environment. The default 90-day window fills holes rather than only appending after the latest stored date. Increase the window for longer outages/history imports.
- Parse actual dates and source draw IDs; split POWER's seventh ball before sorting the six main balls. Validate uniqueness, ranges, integer values, complete results and conflicting duplicate rows. Reject malformed source batches before writing. Ignore future dates and today's results before 18:30 Vietnam time; this publication guard is separate from prediction's existing 18:00 ticket cutoff.
- Each category fails independently, with bounded network timeout and response size. Failed requests retry next interval. Status exposes errors, stale source dates and stored/source conflicts, rather than silently returning success.
- Import is idempotent by category/date and compares the latest stored revision. Existing disagreements (including a missing stored special number) are reported and preserved. Spring transactions use a PostgreSQL advisory lock between scheduler replicas. Node atomically replaces its file before publishing in-memory changes, and is documented as a single-writer JSON server.
- `GET /api/lottery-sync/status` reports the latest in-memory run. Imported data persists with source URL, draw ID and import time; run-status history is not persisted. No schema migration or manual daily seed edits are needed.
- Removed Node's executable hard-coded result bootstrap, including dated 28/09, 16/10 and 17/10 insertions and fake default draws. Existing historical migration data remains untouched. Existing frontend APIs read newly stored results on refresh; no UI redesign was required.
- Live import added 8 MEGA and 15 POWER draws to each JSON data set. Repeating the import added zero rows. The old 24/10 learning report above remains a **pre-sync snapshot**, not metrics for the expanded history.
- Data audit found 15 conflicts in each JSON set: 11 old POWER records lack a special number; 4 records disagree on the main numbers (MEGA 16/10; POWER 15/09, 10/10, 15/10). These need explicit source reconciliation before treating historical prediction metrics as trustworthy. The old report's trio observation is not changed into a predictive claim by this import.
- A clean PostgreSQL install starts from different historical Flyway seeds: the real scheduler added 9 MEGA and 18 POWER draws there, while reporting 13 POWER conflicts. Consequently its history still differs from JSON where conflicting older rows were intentionally preserved.

Configuration and operating instructions: [README.md](README.md). First JSON-resource import: [report](reports/lottery-sync-resources-2026-10-25.json). Node idempotency check: [report](reports/lottery-sync-2026-10-25.json). Actual packaged Spring/PostgreSQL API check: [report](reports/lottery-sync-postgres-2026-10-25.json).

Validation: 19 Node tests and 17 Java tests, TypeScript checking, root build, Angular production `npm run build`, backend `mvn clean install`, and byte-for-byte comparison of all nine Angular assets packaged into the JAR. Live public-feed import was checked through the packaged Spring API against temporary PostgreSQL, including the scheduled repeat. Existing Angular template/deprecation and Java unchecked-code warnings remain.

## Remaining work

1. Consolidate the remaining legacy prediction paths across Node and Spring. The catalog and temporal learner now share definitions, but older algorithm names still map to differing heuristics. Reconcile missing historical seed draws with verified sources.
2. Move legacy bootstrap sample tickets, dated seed explanations, and canned cross-game/reconciliation advice out of executable code into explicitly managed fixture/configuration resources. Historical Flyway migrations were left unchanged.
3. Store forecast snapshots and the exact parameter version used before each draw. Backtests still use the currently active parameter configuration, so they do not reproduce the historical configuration or eliminate retrospective tuning bias.
4. Fit and validate any predictive model on separate chronological training/validation/test sets. Current sigmoid scores, neighbor-risk thresholds, and model names should not be interpreted as measured accuracy.
5. Remove remaining duplicated UI narratives and heuristic thresholds, and move adjustable scoring/selection limits into validated configuration. Spring now returns one calculated neighbor-shift variant in place of the four dated canned variants.
6. Automate the temporary database-backed migration/API checks and expand the shared contract suite before consolidating storage or deploying.
