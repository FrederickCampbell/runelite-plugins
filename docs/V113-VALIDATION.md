# v1.13 consolidation candidate

Baseline: 71a988e63b3794193610a71b8d50faad17a16c93 (v1.12.0-rc1). Development branch only.

## Scope
- Single workflow state with explicit legal preparation and resupply transitions.
- Scoped resource accumulator with sum/max semantics; actual payment allocations determine Guild quantities. Specialized compost, travel and planting-form accounting remain explicit.
- Tree preparation result cached by physical-stock snapshot and allocated crops. No changed-state claim for unrelated systems.
- Protected route insertion uses the same activity ordering as initial routing.
- Shared Swing supply body/row rendering; no intentional visual redesign.
- Calquat registry test, not a verified live fix. Tai Bwo Wannai, Kastori and Great Conch already have region mappings; no speculative acceptance of shared growth varbits outside their mapped regions.

## Validation
CI compares against the exact v1.12 baseline with the same 16 probe fixtures and sidebar previews, runs source and compiled API screening and regression tests, and smoke-tests the developer-mode JAR installer. See the actual run for outcomes.

## Remaining scope
This is deliberately incremental. Full event-driven invalidation of all planning passes, complete conversion of all specialized supply systems to a single ledger and removal of the Sensitive scrollbar script are not claimed. The scrollbar script remains until native visual parity can be demonstrated. No changes to external dependencies, master, Plugin Hub PR or automated game inputs. Live testing of v1.12 and v1.13 remains necessary.
