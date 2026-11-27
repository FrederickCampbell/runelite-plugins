# Changelog

## v1.11.5 — transportation dependency resolution
- Preserve fairy-ring and spell dependencies from public Shortest Path responses. Read owned rune pouch contents through RuneLite, recommend additional loose runes and required fairy-ring staff, and use one travel plan for the checklist, bank priority and inventory budgeting.
- Retain conservative fallback for unresolved routes and quantities; no hidden API, automatic withdrawal or pathfinder duplication. Independent bank-origin routes remain estimates, not a globally optimized itinerary.

## v1.11.4 — unified supply preparation
- Enabled, owned and useful containers enter the ordinary requirement map, inventory slot calculation, checklist and native withdrawal outlines; carried variants satisfy their own requirement.
- Seed-box packing remains conditional on confirmed contents; herb sack and reagent pouch capacity is not assumed. Preparation continues to the bank if bank supplies remain after a vault visit.
- Keep existing storage layouts and player-controlled interactions.

## v1.11.3 — route-aware bank preparation
- Query Shortest Path sequentially from an observed open bank for each distinct actionable destination; prefer matched owned transportation at the top of the native Bank Tags layout.
- Keep static recommendations for unresolved routes; never infer item requirements from ambiguous object/spell edges or override Shortest Path settings.
- Freeze bank promotion after withdrawals or deposits; explicit Show farming bank can refresh it. Compact ordinary categories while preserving equipment silhouettes and the rune matrix.
- Bank-origin estimates are advisory, not a hypothetical multi-leg inventory optimizer. Live navigation and native bank interactions are unchanged.

## v1.11.2 — adaptive run management
- Compact verified Seed Vault sections into one scrollable grid and retain later-wave materials.
- Add optional seed box packing and harvest-container preferences; unknown contents are never credited as free capacity.
- Prefer reachable banks through the Shortest Path multi-target API, including deposit-only resupply.
- Highlight preparation storage and preserve the active guide through loading and stale route queries.
- Delay ordinary noting until inventory space is needed; avoid unnecessary poison ivy compost and confirmed diary-immune crop care.
- Update mutable guide details and supply notes in place; choose the largest feasible preparation prefix rather than forcing a Guild-only wave.
- Live-client acceptance, exact container contents and the reported ALS incident remain subject to manual verification.

## v1.11.1 — release audit

- Match Time Tracking's seasonal growth and regrowth ticks, including profile-specific offsets and the Deadman exception.
- Retain the most recent confirmed patch observation, defer live patch reads behind modal interfaces, and reject invalid remote birdhouse records.
- Ship the sidebar version and upstream notices as standard Plugin Hub resources.

## v1.11 — consolidation and performance

- Reuse one inventory occupancy estimate and native filled-slot count per planning update. Move prefix-invariant reads out of the prefix loop, avoid copying storage without noted payments, and reuse unchanged preparation commitments.
- Skip unnecessary tree material preparation and skip redundant transport capacity checks.
- Preserve the native bank layout when the eligible intersection is unchanged. Use native filled-slot, indexed-item and first-match container APIs; remove superseded seed-box slot calculations, redundant placeholder processing and unreferenced compost boolean helpers. Retain existing manual interactions and lifecycle safeguards.
- Extend comparative CI with preparation, transport, stock and allocation probes, retaining sidebar pixel parity. No new features or dependencies.

## v1.10 — intelligent preparation

- Scope preparation to inventory-constrained contiguous portions of the existing itinerary, with conservative working-space buffers and optional Guild staging.
- Recognize noted gardener protection payments without treating notes as usable planting or compost materials.
- Count carried inventory, verified seed-box contents, equipment and observed transport stock. Budget numeric charge variants and consumable teleports; keep navigation advisory.
- Prepare again at a supply boundary only when the next patch has a known stored shortage; retain the broad farming shelf and highlight missing current supplies.
- Coordinate compatible local compost harvests and stage large compost-bin loads without assuming future yields.
- Keep all gameplay interactions manual. Live-client acceptance remains separate.

## v1.09 — local farming workflow

- Prefer verified nearby selected plots over remote work before a patch is underway, without changing seed allocation or the pinned itinerary.
- Combine distinct tool withdrawals and ordinary compost quantities for verified plots sharing a nearby tool leprechaun, reserving two free inventory slots for optional collection. Continue the collection while the native exchange remains open.
- Suggest noting recognized farm produce when inventory is nearly full, preserving required supplies and local compost-bin materials.
- Track verified local crop planting ahead of the active step using existing transition logic, preserving protection requirements and keeping partially completed patches active.
- Highlight the leprechaun and safe inventory-relief item, including surplus empty buckets within verified native storage capacity.
- Retain manual interactions, special transport legs, explicit skips and existing crop preferences.

## v1.08 — runtime refactoring and compost lifecycle

- Read verified native compost tiers for physical patches, preserving pretreated soil and preventing stale treatment acknowledgements after crop removal. Retain conservative historical fallback and explicit manual skips.
- Cache shared patch regions, reuse stock and supply calculations, reduce overlay allocation, and consolidate watering stages and storage decoding.
- Classify checklist supplies once in stable category order and avoid intermediate filtered guide-kit lists. Preserve existing manual gameplay, preferences and integrations.
- Compare baseline and candidate regression tests, Java 11 classfiles, allocation fixtures and sidebar pixels in CI. Live-client acceptance remains separate.

## v1.07.7 — watering lifecycle correction

- Record verified wet observations for every selected local waterable patch, including crops watered ahead of the guide. Reset suppression on verified empty/dead state; do not offer repeat watering merely because growth advances.
- Centralize watering offer settlement and clear obsolete offers on cursor changes, interruptions, preparation and world hops. Bind sidebar skip intent to the displayed action so delayed watering skips cannot skip an unrelated action.
- Preserve manual interactions and existing optional watering policy; no new polling or dependencies.

## v1.07.6 — final supply convenience

- Recommend magic secateurs for yield-bearing harvests, recognizing inventory and equipment; unknown or locally stored tools remain advisory.
- Guide one compost and one filled watering can withdrawal in the same leprechaun visit when both are needed anywhere in the remaining run and fit. Highlight both native slots, retain the remaining instruction after the first withdrawal, and preserve compost priority when space is limited.

## v1.07.5 — stabilization release candidate

- Fix a deferred farming-bank stage transition when the player browses a personal tab and returns with unchanged stock.
- Add a release gate for version/class metadata, a broader production forbidden-API source screen, maintained/inherited Java line counts, and review-sensitive call inventory.
- Preserve all v1.06.1–1.07.4 behavior; document final review scope and player-side acceptance requirements. No new gameplay features.

## v1.07.4 — stabilization branch GUI and code consolidation

- Reuse sidebar priority controls on reorder/remove/add, update patch enablement counts without rebuilding rows, and preserve same-session guide header controls. Share disclosure state, native button creation, column panels, and HTML escaping.
- Share selection/eligibility and local crop discovery between planning, disease and watering. Reuse skill reads and existing observations. Keep independent crop decisions, explicit skips, specialty stages and seedless rules.
- Consolidate duplicate seed-box pickup parsing, native produce verification, run selection and bank choice. Parse crop preferences once per allocation type, index native contract metadata once, and count sapling preparation stages in one pass. No new dependencies or global input/focus APIs.

## v1.07.3 — stabilization branch runtime reliability

- Move game-state initialization and teardown to the client thread; guard deferred profile, config, plugin-message and Swing publications against old lifecycle/session identities. Coalesce obsolete guide views and release run/scene/icon state on disable.
- Shortest Path clears only after an exact, current, unmodified destination response; legacy silence never permits a global clear. Bound release checks and use unique request IDs across bridge restarts.
- Avoid allocating inventory ID arrays in item overlays; classify repeated scene object definitions once per scene and release despawned world views. Remove duplicate navigation state and widget-open polling; use the existing game-tick storage sampler.
- Centralize run mutation guards and preference refresh, avoid unchanged bank shelf reconciliation, reset native service registration on replacement, and document consolidation targets for v1.07.4.

## v1.07.2 — stabilization branch storage reliability

- Treat incomplete native storage reads as unavailable rather than empty; commit bank/vault container changes after packet reconciliation. Persist per-character last successful observation timestamps, including confirmed empty storage. Keep placeholders separate from quantities.
- Preserve pending seed-box actions across unrelated clicks, invalidate uncertain overlapping transfers, reject incomplete native reads, correlate delayed pickup messages with native increments, and wait for delayed check lines. Never promote remembered counts to carried stock.
- Leave unverified vault sections entirely native, detect replacement icons/labels/dividers as well as headers, and restore only owned scroll extensions. Release stale Bank Tags service references. Guard integer addition and retain native item identities/actions.

## v1.07.1 — stabilization branch farming correctness

- Verified live dead crops re-enter shared planning even after earlier completion, preservation or an optional watering detour. Explicit skips, eligibility gates and existing seedless policies remain authoritative.
- Invalidate planting/cure evidence on death, reject invalid local decoder values instead of falling back to historical growth, prefer the last verified local snapshot on same-second tracker conflicts, and reject malformed remote observations.
- Recognize the inherited hops decoder's documented extra weed/rake values. Add regression tests for every dead hops species, shared live-loss accounting, clear-only completion, replanting, watering detours, historical conflicts and explicit skips.

## v1.07.0 — stabilization branch overlay performance

- Highlight every verified crop plot with one ground-level rectangle, derived from actual scene tile bounds. No crop model clickboxes are calculated, even for dense hops and allotments. Keep camera projection live, including the actual world view and plane.
- Reuse cached patch footprints for navigation approaches. Cache local leprechaun, gardener, transport and specialty object lookups, including absent targets, until a scene/tick or relevant event changes them. Preserve single-object bank, boat, compost-bin and birdhouse outlines, cyan labels, green arrows and optional-care indicators.
- Add regression coverage for dense patches, even-sized footprints, scene isolation, cache invalidation and repeated-frame projection. Correct the previous hardcoded release-version assertion.

## v1.06.1 — stabilization branch compliance hotfix

- Remove global desktop focus inspection and explicit focus requests from sidebar code and tests. Keep scroll anchoring and native Swing keyboard traversal; retain the existing search field during filtering and access refreshes.
- Replace the desktop-focus regression with isolated component-action, ranking and search-selection assertions. No display-focus opt-in is required for this test.
- Add a source-level input API guard to the Gradle test task and enable CI for the stabilization branch. No gameplay actions, crop guidance, storage views or navigation behavior changed.

## v1.06

- Fix live-state recognition for underwater seaweed despite plane-zero map markers; preserve underwater region/plane checks and resolve tool leprechauns in the player's current world view. Restores shared contextual compost and withdrawal guidance.

- Seed-box reliability: confirm closed-box transfers from player actions and loose-inventory/storage deltas, reconcile native contents and explicit game messages, track verified planting consumption, and keep character-local historical quantities without treating stale counts as current stock. Existing cyan inventory guidance remains shared.

- Add the default-on **Prefer contracts** checkbox under Crop priorities. Reserve one available active contract crop for an eligible selected Guild patch before manual and automatic preferences; fall back normally when unavailable. Keep existing living-contract protection independent.

- Require explicit display-test opt-in for the OS-dependent Swing keyboard focus regression; CI continues to run it on an isolated virtual display. Normal installer builds retain all other tests.

- Track the native seed-box container in slot order during the current session. Carried contents count toward crop allocation and supply readiness without inflating loose inventory or bank totals. Unknown contents require checking the box.
- Highlight the seed box for planting when the selected crop is stored inside; recommend direct planting only when its first compatible seed matches the plan. Otherwise recommend manual withdrawal. Birdhouse bait and tree preparation remain manual.

## v1.05

- Restore compact Seed Vault filtering; hide irrelevant placeholders and empty categories without changing native item identities or actions.
- Guide watering of eligible live, unwatered crops; recognize filled watering cans and Gricoller's can in leprechaun storage. Skip watering independently of the patch.
- Prefer carried supplies, then leprechaun storage, then bank stock. Hide covered compost and watering-can requirements; highlight the leprechaun and patch together when local supplies are needed.
- Add character defaults and temporary run overrides for watering and leprechaun assistance. Pin Start Run and Continue Run at the top of preparation.
- Large green leprechaun arrow and green outline, cyan Compost label with a red cross for pending optional compost on the active patch, and native exchange-slot guidance with a bounded withdrawal quantity for the actual stored can or compost.
- Contextual cyan action labels; green leprechaun arrow, outline and single exchange slot; red crosses for optional care. Immediate withdrawals use one item and native storage counts. Optional watering no longer gates planting completion and automatically expires after a short local offer.
- On the seaweed bank island, actual birdhouse inventory gates the rowboat; a missing kit highlights the bank chest with a green arrow, and generic routing resumes only after leaving the island. Correct underwater leprechaun detection and tolerate native camp dialogue formatting.
- Public author name: Freddy. Upstream credits and license notices remain intact.

## v1.04

- Guide the seaweed-to-birdhouse transition via the anchor rope, nearby bank chest when needed, and rowboat dialogue. Prefer sufficient carried birdhouse supplies before bank alternatives.

## v1.03

- Preserve fixed nearby patch pairings in mixed runs and guide local ultracompost withdrawals from tool leprechauns.

## v1.02

- Recommended items and Recommended teleports appear above preparation, resupply and patch steps. Each header has its own inline help button. Both lists collapse when the route starts or resumes. Start Run and Continue Run stay within reach while you scroll.
- The farming bank layout and compact Seed Vault keep native placeholders through withdrawals and deposits. A placeholder contributes zero to stock, crop choices and teleport recommendations.
- Supply rows show carried quantities and positive amounts last seen in storage. The missing-items count covers required supplies. Show farming bank explains when it needs an open bank or the Bank Tags plugin.
- The sidebar preserves position and keyboard focus as you edit. Manual and grouped selections update in place, and Individual Run search reuses its buttons. Long labels wrap at the available width.
- Character preferences and temporary run overrides are labeled. Locked patches show their reason and remain editable. Skip and stock reset dialogs name the action and default to Cancel.

## v1.01

- Custom Seed Vault views reapply their compact layout after deposits, withdrawals and depleted stacks. Native item slots and actions stay attached to the original widgets.
- Preparation updates its destination when newly observed storage changes the best supply source. Gathering the supplies releases the helper’s preparation route.
- Seeds and seedlings count as materials to prepare. The guide shows how many plantable saplings you actually carry.
- Species controls refresh when their crop-type default changes. Teleport recommendations distinguish nearby plots with different destinations. Newly actionable patches can join the remaining itinerary.
- The guide shares physical patch identities, crop definitions, stock observations and live scene objects across planning, highlighting and storage views.

The [release audit](docs/V1.01-AUDIT.md) records the subsystem counts and remaining live checks.

## v1.0

First stable release of grouped, individual and manual runs, stock-aware preparation, crop preferences, optional teleports and contextual help.

## Earlier milestones

| Versions | Changes |
| --- | --- |
| v0.42–0.50 | Species and patch preservation, replacement precedence, optional travel items, physical planting requirements, storage freshness and a smaller guide sidebar. |
| v0.39–0.41 | Player-controlled preparation, clustered routes, carried replacement checks, transport instructions and final sidebar artwork. |
| v0.33–0.38 | Contextual bank shelves, teleport eligibility, profile and interface cleanup, native layout ownership and Seed Vault rebuild handling. |
| v0.27–0.32 | Birdhouse stages, priority disease care, Fossil Island travel, optional Bank Tags shelves and sidebar navigation. |
| v0.20–0.26 | Grouped and individual presets, compost upgrades, stricter Kastori flower verification, run-aware vault filtering and manual templates. |
| v0.13–0.19 | Standalone patch guidance, live completion evidence, crop and supply ranking, scene highlights and compact Seed Vault views. |
| v0.1–0.12 | Quest Helper farming-state integration, Farming Patch Advisor catalogs, optional Shortest Path routing, per-patch settings and standalone run execution. |

The full beta changelog and imported Farming Patch Advisor history are preserved in the [v1.0 archive](https://github.com/FrederickCampbell/runelite-plugins/blob/2a86531/CHANGELOG.md).
