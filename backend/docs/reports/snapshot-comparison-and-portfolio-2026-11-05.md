# POWER / MEGA: paired snapshot comparison and diverse ticket option

## Comparison

The draw analysis report now includes `modelComparisons`. In the multi-draw evaluation section,
each row pair compares Logistic with one primary algorithm using one frozen ticket per side.
Logistic always uses slot 0; the remaining four Lab tickets never influence which ticket is selected.
This evaluates the top-ticket policy, not the performance of the entire five-ticket Lab portfolio.

Each pair uses only common target dates up to the report date with the same training history hash.
Both snapshots must pass the existing prospective validation, including the 18:00 Vietnam cutoff,
history/configuration hashes and the immediately preceding scheduled draw. Lab batches must have
all five distinct tickets and slots. Ambiguous duplicate snapshots are excluded. Model, code and
configuration versions remain separate cohorts. Comparisons are independent of the algorithm
selected for the single-draw explanation; the category selector chooses POWER or MEGA.

Metrics include matches per ticket and rates of draws matching at least three/four main numbers.
The difference is Logistic minus the other algorithm. Its approximate 95% interval uses paired
per-draw differences; it is unavailable with one draw. Counts below 30 are marked insufficient.
These are descriptive historical results, including multiple comparisons, not a model promotion rule.
Missing historical snapshots are never reconstructed. Strict matching may leave little or no data.

## Three-ticket option

New READY workspace suggestions include a separate frozen `portfolioTickets` option. It applies
the existing `rank-portfolio-v1` selector to equal-weight reciprocal rank fusion of the initial two
full rankings. The first ticket contains the highest ranked six numbers. The fixed three-ticket
budget contains distinct tickets, with at most three shared numbers between any pair. All numbers
remain eligible. The policy and overlap/number-use diagnostics are stored with the suggestion.

The user chooses or edits these tickets through the normal commit flow. Source identity,
original numbers and equal-budget frequency/random controls are retained. The initial primary,
secondary and consensus outputs remain available. Recombining the consensus does not change
the frozen portfolio option. Existing drafts without portfolio fields remain supported.
This option does not claim increased predictive accuracy; subsequent committed results are needed.

## Validation

- Backend unit and HTTP tests cover both categories, fixed slot selection, common dates/history,
  incomplete/late/duplicate snapshots, version separation, paired uncertainty, portfolio overlap,
  rank preservation and commit provenance.
- Frontend tests cover paired metrics and selecting/committing a portfolio source.
- Production frontend build and TypeScript checks run locally. No production data was changed.

An existing Lab projection test assumed two models while its fixture generates all three current
models. Its expected origin counts now follow the fixture's model count.
