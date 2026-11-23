# Ironman Farming Director development history

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
