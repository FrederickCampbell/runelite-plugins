# Ironman Farming Director development history

## v0.7.0-beta.1 — Quest Helper interaction parity pass

- Imported the original 32 TreeRun/HerbRun patch object IDs and 22 gardener IDs from Quest Helper, preserving its attribution and using the existing DirectorWaypoints coordinates. Event-tracked exact matching precedes the conservative Advisor fallback.
- Separate tree removal (pay or chop), stump clearing, raking, planting, compost and protection actions. Budget 200 coins per potentially paid removal; manual chopping still needs an axe.
- Profile-scoped compost selector supports none, regular, super, ultra or filled bottomless (ultra fallback), preserving legacy boolean settings. Both protection and compost may be enabled independently.
- Active interaction snapshot drives route item/spellbook highlights, patch/NPC world hints, specific gardener dialogue choice outlines, active inventory highlights and sidebar. Generic confirmations are left manual to avoid false matches.
- Current patch card shows observed action transitions; advanced details are tucked away. Existing planner, storage scanner and Shortest Path integration remain.
- Added regression tests for exact targets, action selection, removal coins, dialogue matching, travel hints and compost migration/stock. In-client testing remains required, particularly ambiguous specialist patches, garden dialogues and care confirmation.


## v0.6.1-beta.1 — In-world guidance and simple workflow

- Reused Advisor's patch-object tracking and footprint overlay; adapted Quest Helper's active ObjectStep clickbox, down-arrow and item-icon presentation to the Director's actual planned step.
- Highlight the active patch, or its registry-defined gardener during protection, rather than all nearby recommendations. Suppress ambiguous targets instead of guessing; configurable highlight color and visibility.
- Simplified the default guide to current action, destination, progress, next stops and navigation. Advanced controls and planning details remain available on demand.
- Live supply checklist with carried quantities; wait at preparation until Begin farming, then request Shortest Path and enable progress tracking. Replan after banking.
- Group default mixed itineraries by location; preserve explicitly saved merged route order.
- Newly planted crops awaiting configured compost remain on the compost step until care confirmation or manual Next. Retain previous state readers and planner.
- Tests cover target matching (exact, unique and directional pairs), ambiguous numbered patches, stable location grouping and compost action transitions. Live RuneLite visual and care-tracking behavior still requires player testing.


## v0.6.0-beta.1 — UI polish and final integration pass (2026-11-23)

- Reworked sidebar spacing and scroll behavior for RuneLite's native panel width; highlighted current/completed stops and added progress bar, active destination, compact navigation and a separate readable loadout page.
- Added visual crop ranking and stock reserve controls, retaining the previous profile encoding and the existing Planner/CropCatalog.
- Added a home-screen merged preset picker, deletion and cleanup, per-preset/category reservations, omitted-route-stop restoration, and patch counts/attention filter.
- Connected existing Advisor bank filter, bank and gameplay checklist overlays and item outlines to the active remaining Director wave. Kept the legacy fallback and independent contract checklist.
- Calculated configured travel slot costs using actual consumable item stackability; added player-pinned per-patch waypoint navigation, explicitly separate from crop state.
- Improved payment handling: available and feasible tree payments are allocated, infeasible/missing payments are reported while allowing the preferred crop; harvest-only checked trees are retained.
- Exposed per-character compost preference and harvest space reserve; filtered known locked compost destinations; improved profile-safe status refresh and new-run confirmation.
- Added regression coverage for priority controls, reserve serialization, travel costs, player waypoints, narrow viewport policy, protection feasibility and active-bank checklist projection.
- **Beta:** automated builds are not a substitute for RuneLite/Windows playtests. Exact specialist routes, remaining transport eligibility/charge rules, actual harvest capacity, live overlays and GUI behavior still require verification.


## v0.5.0-beta.1 — Functional integration and hardening (2026-11-23)

- Reused the existing planner and wave splitter; applied slot limits to harvest-only and protection-only stops and allowed planning to continue past an impossible first stop.
- Added per-wave rake/spade/dibber/secateurs and compost requirements, ordinary bucket slot costs, filled-bottomless-bucket option, source-aware withdrawals and missing-supply warnings. Axe alternatives, charges and harvest yields still need live verification.
- Replanned at banking checkpoints rather than continuing with obsolete waves; fixed manual destination jumps so earlier unfinished stops remain in the itinerary.
- Reconciled known unlock checks with upstream Quest Helper's HerbRun and TreeRun requirements, including Varlamore, Fossil Island, Harmony diary, Prifddinas, Sailing and Farming Guild tier access.
- Audited Advisor/Quest Helper patch identity coverage and added specialized health-check and anima-expiry normalization; protected permanent spirit trees from routine removal guidance.
- Extended the Compost Run into a sequential, manual guide with per-bin supply estimates and optional observed/nearby-area route requests.
- Added a profile-specific Shortest Path switch and reviewed the upstream path/clear message contract.
- Hardened logout/profile handling, asynchronous planning and storage observations; ignored closed-interface bank/vault events and preserved saved route membership when global exclusions change.
- Added regression tests for the above pure models and recorded an in-client acceptance checklist.
- Remaining: live RuneLite/Windows testing, precise specialist destinations, full teleport/charge and spirit-tree-limit modeling, visual/interaction refinement, complete legacy-control migration.


## v0.4.0-beta.1 — Unified sidebar and global Patch Manager (2026-11-23)

- Replaced the visible Guide/Advisor tab arrangement with one sidebar and five pages: Run Selection, Active Guide, Run Editor, Patch Manager and Global Settings; added a dedicated Compost Run observation page.
- Preserved the Advisor backend (crop catalogs, bank/vault stock, timers, contracts, protections, existing overlays) and optional Shortest Path navigation.
- Added a global per-character patch-enabled registry. Physical patch names are expanded from Quest Helper before exclusions apply, so an individual allotment can be excluded without disabling its neighbor.
- Implemented collapsible/searchable patch categories, individual switches and bulk location/category choices; displayed known unlock restrictions and independently observed readiness, including unverified states.
- Added saved, reusable Merged Runs, combining any Advisor farming categories through the existing Director planner and wave splitter.
- Added per-category priority inheritance with optional per-preset priority overrides. Regular crop selections remain automatic or manually ranked; current-run and permanent single-patch crop overrides now have distinct scope.
- Moved most planner controls out of the active guide. Added ready-only and include-unverified preferences, a shorter active-guide display with optional diagnostic details, and inline patch-edit actions.
- Moved route ordering and travel choices into another sidebar page; merged-run presets retain independent itinerary and teleport choices.
- Added an Advisor-backed farming status page exposing observed timers and farming contracts without restoring a second sidebar GUI.
- Preserved completed steps and active mode while opening editor/settings/patches; banking replans continue with the active run and respect newly disabled patches.
- Added regression tests for merged priorities, patch expansion/exclusions, saved profile isolation and active-run mode selection.
- Known limits: patch unlock verification is incomplete; the compost page shows existing observed actions and supplies but not a full step-routed itinerary; some older Advisor controls need equivalent unified UI; no in-game/Windows client acceptance testing has been performed.


## v0.3.0-beta.1 — Intelligent banking and loadout planning (2026-11-23)

- Built a multi-wave planner that preserves unplanned patches instead of dropping the remainder at 28 slots.
- Added bank checkpoints with user-selected or approximate nearest-listed bank waypoints; supports optional Shortest Path event routing and manual next-wave confirmation.
- Source-aware inventory/bank/Seed Vault ledger with estimated per-wave withdrawals and missing-item breakdown.
- Added inventory stop limits and dynamic per-wave teleport slot budgeting, excluding equipped travel items.
- Counted consumable teleport uses, warned on missing configured travel items, and retained the existing Advisor route preferences.
- Added safe auto-advance for harvest-only stops only after an observed live state transition to empty.
- Kept manual crop and route overrides, tree protection gating, resume/replan and Herblore-focused presets.
- Expanded regression tests for planning waves, bank eligibility, source-aware stock, travel supplies and progress safety.
- Java build and tests run on GitHub Actions; in-game RuneLite verification remains outstanding.



## v0.2.0-beta.1 — Consolidated farming director (2026-11-23)

- Generalized Guide to every patch type in Advisor's crop catalog, including multi-type combined runs.
- Individual Quest Helper patch decoding for herbs, trees, fruit trees, calquats, hardwoods, allotments, bushes, hops, flowers, seaweed, grapefields, cactus and specialist patches.
- Added exact Quest Helper fruit/calquat/hardwood coordinates, observed Advisor targets and clearly labelled non-routable region hints.
- Automatic state-driven actions and step progression with manual overrides and optional Shortest Path integration.
- Global 28-slot seed/sapling inventory planner, grouped-patch splitting, reserved seed stock, tree protection, and harvest-only stops.
- Individual crop overrides, Herblore-supplies preference preset, optional nearest-stop proximity heuristic and opt-in replan after banking.
- Saved per-character run checkpoints, resume after relog, and replan-remaining workflow.
- Streamlined guide into a compact sidebar with expandable planner options and preserved Advisor tab.
- Unit tests and GitHub Actions builds. Interactive in-client verification is still outstanding.



## v0.1 — Quest Helper farming-guidance integration (2026-11-23)

- Preserved Farming Patch Advisor's implementation as the main engine, with a separate plugin name and configuration.
- Ported Quest Helper's actual FarmingWorld region/patch mapping, varbit decoder, crop states and Time Tracking prediction logic under a standalone package with BSD attribution.
- Added live condition-based instructions for herb and tree runs: harvest, check health, clear, cure, dig, plant and gardener protection.
- Added automatic move-to-next after observed growing/protected states, click-to-jump destination list, Pause/Resume and manual fallback. Shortest Path receives the same message structure as Quest Helper.
- Added allotment runs, splitting Advisor's grouped allotments into Quest Helper's independently tracked patches. Allotment seeds count as stackable in the loadout.
- Improved quantity planning to skip known already-growing patches and retain existing trees requiring protection without consuming another sapling.
- Added tests for patch access and Quest Helper state decoding, step behavior, seed priorities, reserves and inventory limits.
- Gradle build and tests run in GitHub Actions; interactive RuneLite testing still required.

---

# Farming Patch Advisor upstream version history (imported without reinterpretation)

## V1.11 — 2026-09-26

- Fixed stuttering and short freezes when **All** is selected in the Farm Run
  side panel.
- The panel now preserves its existing cards and updates countdowns, growth
  stages, status colors, and compost timers in place once per second.
- Full card reconstruction now happens only when the underlying patch,
  contract, compost-bin, inventory, route, or plugin-setting data changes.
- Hidden side panels no longer perform periodic visual refresh work.

## V1.10 — 2026-09-24

- Added a **Farm run** selector inside **Route & Teleports** containing every
  enabled crop-run option currently available in the side-panel dropdown.
- Switching runs inside the pop-out now loads that run's independent patch
  order, membership, and teleport selections without closing the editor.
- Keeps edits for every visited run as drafts and saves them together only when
  **Save route** is selected; **Cancel** still discards all changes.
- Each selected patch continues to offer only travel methods that reasonably
  serve its location, allowing players to choose the method they own and use.
- Groups allotment and flower patches at the same farming location into one
  route stop displaying only the location name. The shared teleport choice is
  applied to every underlying allotment and flower patch at that stop.
- Added **Reset selected run**, which restores the active run's default order,
  membership, and `None / no item needed` teleport choices when the editor is saved.
- Added regression coverage for run-selector availability, combined tree runs,
  and lossless grouped-stop expansion.

## V1.9 — 2026-09-24

- Fixed the RuneLite client appearing frozen when **Compost Run** was opened by
  making the compost target config enum accessible to RuneLite's config proxy.
- Removed the duplicate fill quantity from compost action text. Filling bins now
  show the amount once in `Status: FILLING x/x`, followed by a clean action such
  as `Add Watermelon`.
- Confirmed in-game on Sir_deadmeat that standard and big-bin capacities, state
  transitions, timers, persistence, settings synchronization, scene highlights,
  movable checklist, bank-side checklist, and bank filter all work correctly.

## V1.8 — 2026-09-24

- Added a dedicated **Compost Run** to the Farm Run dropdown for Falador,
  Morytania, Catherby, Ardougne, Kourend, the Farming Guild, Civitas illa
  Fortis, and Prifddinas.
- Added character-specific compost-bin state tracking using RuneLite's current
  standard and big-bin farming varbits. Cards show contents, fill amount,
  processing countdown, readiness, and the next useful action.
- Added clean whole-bin scene outlines: gray when empty or unknown, orange while
  filling, white while processing, and green when ready or being collected.
- Added configurable compost targets, individual bin locations, ground
  highlights, and supply/bucket checklist controls under **Compost bins**.
- Added compost-run supplies to the movable checklist, bank-side checklist,
  bank filter, and item highlights. Compost potion bottles count their actual
  doses, and an owned bottomless compost bucket replaces ordinary buckets.
- Based the state ranges and regional transmit varbits on RuneLite's open-source
  Time Tracking farming implementation, with regression tests for standard and
  Farming Guild big compost-bin states.

## V1.7 — 2026-09-18

- Fixed the Civitas illa Fortis patch changing from `READY` to `DEAD` when
  harvesting began. A temporary `Clear` action during a confirmed harvest now
  remains a live harvest transition.
- Explicit dead and diseased states still take priority, and completed timers
  are removed only after the patch object becomes genuinely empty.
- Refreshes the pending harvest state on every harvest or pick action so
  multi-pick crops continue to be evaluated from their latest object state.

## V1.6 — 2026-09-18

- Audited every protection payment used by the plugin against the current Old
  School RuneScape Wiki, including allotments, hops, bushes, trees, fruit trees,
  hardwoods, special patches, cacti, seaweed, and coral. All 51 existing payment
  item IDs and quantities were correct.
- Updated payment labels to use exact in-game item names where the checklist had
  used informal plurals, such as `Coconut`, `Watermelon`, and `Ground tooth`.
- Added an exhaustive regression test for every payable crop in the catalog and
  for crops that correctly have no gardener protection payment. Grape-vine
  saltpetre remains a planting requirement, not a protection payment.

## V1.5 — 2026-09-18

- Added **New custom route**, **Add patches**, and **Remove selected** controls
  to **Route & Teleports**. Players can build a route from enabled patches in the
  selected run, then arrange them by dragging or with the move buttons.
- Limited each patch's teleport dropdown to travel choices that reasonably serve
  that patch's area. `None / no item needed` remains available everywhere.
- Invalid older teleport selections safely fall back to no required item, while
  disabled route stops retain their saved position and membership.

## V1.4 — 2026-09-18

- Fixed living, harvestable cactus and potato cactus patches being marked dead
  when their object actions include both a harvest option and `Clear`.
- Explicitly dead plants and non-harvestable plants whose only applicable patch
  action is `Clear` continue to use the red `DEAD` state.

## V1.3 — 2026-09-17

- Corrected all five hardwood growth timers to match RuneLite's 640-minute
  growth cycles: teak 4,480 minutes; mahogany, camphor, and ironwood 5,120;
  rosewood 5,760. Inspected stage estimates now use the same cycle length.
- Added checks for planted and inspected stage calculations for every hardwood.
- Existing saved hardwood timers using the old estimates are corrected when that
  character's timers load; unrelated timer values are left intact.
- These are maximum estimates until a character's growth-tick offset is known;
  RuneLite Time Tracking can predict a more precise completion time.

## V1.2 — 2026-09-17

- Sidebar patch and contract cards now grow to fit wrapped text instead of relying
  on fixed heights, including long names, payment details, timers, and teleports.
- Compact header buttons fit the RuneLite sidebar; the route editor adapts its
  scroll area to the available screen and wraps long stop names.
- Local visual polish pending in-game confirmation.

## V1.1 — 2026-09-17

- Added a Route & Teleports editor with drag ordering, up/down controls, default
  order, Save, and Cancel; preferences belong to each character and run selection.
- Custom routes follow enabled patches and locations, with matching panel and
  ground-label patch numbers. Disabled stops retain saved choices.
- Added teleport checklist sections, equipped-item detection, and bank filter/highlight
  support for chosen supplies. Reusable items are deduplicated and tablets count per visit.
- Local update; in-game verification and publication are pending.

## V1.0 — 2026-09-17

Established the current local plugin as the V1.0 baseline, including the existing
Morytania patch-matching fix, performance optimizations, and harvest-status changes.
These local changes still require in-game confirmation and publication.

Teleport checklist and custom route ordering are preview concepts and are not
included in V1.0. The next update will be V1.1, followed by V1.2, V1.3, and so on.
