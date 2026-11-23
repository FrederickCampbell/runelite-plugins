# Ironman Farming Director — v0.5.0-beta.1

A standalone, integration-first RuneLite farming assistant for ironmen. **This is
a developer beta, not an in-game verified production release.** The v0.5 branch
preserves Advisor's crop, patch, compost, stock and banking infrastructure and
the vendored Quest Helper farming-state reader, then replaces the old Guide /
Advisor tab layout with one multi-page sidebar.

## Architecture

- **Farming Patch Advisor** is the backend for crops, patches, growth timers,
  contracts, compost, payments, teleports, bank and Seed Vault observations.
  Its original panel is no longer a visible tab, but the implementations and
  existing overlays are retained. Some specialized legacy panel controls still
  need further unified UI refinement before v1.0.
- **Quest Helper** farming-region, patch-state and varbit logic is vendored under
  `com.farmingpatchadvisor.queststate`; the external Quest Helper plugin is
  not required. The sidebar uses Quest Helper-style stages and manual controls,
  not the actual Quest Helper component hierarchy.
- **DirectorPlanner** and **DirectorWavePlanner** remain the sole crop allocation
  and 28-slot loadout planners. Merged runs use the same engines, with per-category
  crop priorities. No second crop catalog was added.
- **Shortest Path** is optional. The Director chooses the next stop, then sends
  plugin messages to Shortest Path for navigation. Straight-line nearest-stop
  ordering is only a heuristic, not proven teleport-aware route optimization.
- **Global Patch Manager** stores one per-character enable/disable choice per
  actual Quest Helper patch identity when available. Legacy area toggles provide
  the initial default until the user explicitly chooses a patch.

## Installation — RuneLite Dev Manager

Download the v0.5 branch ZIP:

https://github.com/FrederickCampbell/runelite-plugins/archive/refs/heads/ironman-farming-director-v0.5.zip

Extract its Gradle project into:

    C:\_Hub\Projects\Gaming\OSRS\RuneLite\Plugins\Ironman-Farming-Director\

Use **Refresh → Build → Launch** in RuneLite Dev Manager. A normal local build
from the project root uses `gradlew.bat clean test build`. CI uses Java 21 to
run Gradle while the source targets Java 11. Neither the Windows launch
workflow nor in-game behavior has been tested by this CI.

## Using the sidebar

1. Log into a character and scan your **bank** and **Farming Guild Seed Vault**
   so the Director can use observed stock. Stale snapshots are not presented
   as live stock. Choose **Herb Run** (default) and press **Start farming run**.
2. The **Active Guide** shows the current farming action, next patches,
   requirements, and Back / Next / Pause / End controls. Use the pencil icon
   to override a crop for this run or save it permanently. Expand **planning
   details** to inspect warnings, payments, travel items and withdrawal estimates.
3. The **Run Editor** holds automatic crop selection or a comma-separated
   manual priority order, plus stock reservations. For merged runs select a
   named preset, check the run categories, and save. A merged run inherits each
   category's crop choice by default; optional per-category overrides can be
   saved for that preset. Category changes are not applied until Save. The
   **Route Editor** is also a page inside the sidebar: reorder or skip stops,
   choose supported travel items, and save. Merged presets preserve their own
   route membership and teleport choices instead of modifying the general
   All Enabled route.
4. The **Patch Manager** shows individual known patches grouped by type and
   location. Search, expand/collapse categories, toggle individual patches or
   use location/category bulk controls. Disabled means **excluded globally**
   from regular, all-enabled and merged plans. Unknown patch state is marked
   unverified rather than assumed ready. The visible unlock description
   intentionally reports only known requirements.
5. **Global Settings** contains auto-next, ready-only filtering, automatic
   replan after bank closure, approximate nearest-stop order, gardener payment
   preference, unverified-location inclusion, inventory-wave limit and bank
   choice, and an explicit Shortest Path switch. Editing these pages never ends an active Guide.
6. After a banking checkpoint, reopen storage and choose **Replan remaining**.
   Completed patches are retained. Bank checkpoints replan instead of reusing a
   stale future loadout. **Resume saved run** restores the saved
   mode and completed physical patch keys for that character.
7. Choose **Compost Run** for a sidebar readout of the existing Advisor
   compost-bin state, per-bin supply estimates, manual Back/Next and a Route
   action. Routing uses observed bin objects or verified nearby herb areas;
   no invented exact tile for unobserved Guild/Prifddinas bins. Compost
   progression is manual, and maximum ingredient estimates are not a claim
   that an unknown bin is empty. The
   **Timers & farming contract** page displays currently observed Advisor
   timers and the latest recorded farming-contract assignment.

Run Selection lists Advisor's nine original categories, plus All Enabled,
Compost, and Merged. Separate legacy combinations remain internally supported
for older saved checkpoints but are no longer shown as primary run options.

## What changed in v0.5

- Cross-checks the Advisor patch registry against the vendored Quest Helper
  patch names; refuses ambiguous grouped patch identities.
- Corrects known access gates using upstream HerbRun/TreeRun, RuneLite quest
  state, Morytania elite diary varbit, Sailing skill and Farming Guild tiers.
  Partial quest progress remains unverified. Boosts, transport restrictions,
  spirit-tree planting limits and newly introduced content need further checks.
- Normalizes known special patch stages: expired anima, health checks for
  spirit/redwood/celastrus/crystal trees. Spirit trees are not intentionally
  cleared as part of normal harvesting; Hespori instructions distinguish the
  combat/harvest step.
- Budgets real tool and compost item slots for each selected wave. Harvest-only
  and protection-only stops still pass the same inventory limit. Impossible
  first stops no longer block every otherwise viable stop.
- Keeps unfinished earlier steps when the user jumps forward; bank checkpoints
  replan from remaining identities, including changes to global exclusions.
- Rejects stale asynchronous account results, resets the active session on
  logout and preserves per-account checkpoints. A planning-in-flight guard
  prevents old step observations from completing a replaced plan.
- Provides sequential compost guidance using the existing bin manager and
  an optional navigation toggle. Shortest Path's current source still accepts
  the path/clear message contract; actual client behavior is not verified.
- Bank and Seed Vault snapshots are accepted only while their interfaces are
  open; closing drains are ignored once the close event is received. Verify
  actual widget/container event ordering in the live client.

## State, unlocking and exclusions

**Enabled** is a saved user preference. **Unlock** is derived only where a
known Farming-level or quest requirement is implemented, and otherwise remains
unverified. **Ready** is derived from Quest Helper / RuneLite observations.
These properties are independent: an enabled patch can be growing or unknown,
and a disabled patch can still be unlocked. Ready-only selection omits unknown
states rather than treating them as empty.

Patch-manager entries originate from Advisor's patch catalog, expanded using
Quest Helper's actual patch names whenever a canonical match exists. When
Quest Helper cannot resolve a grouped stop, the manager leaves it grouped and
the planner conservatively refuses to invent subpatch identities. Global
exclusions take precedence over run membership and manual route selections.

## v0.5 regression and playtest checklist

GitHub Actions runs `bash gradlew clean test build --stacktrace`. The tests
cover patch identity splitting, global exclusion semantics, merged categories,
category-specific crop priorities, per-profile saved choices, and preserving
the active run mode while viewing another mode. New tests exercise catalog
crosswalks, special state normalization, inventory capacity, progress after
manual jumps, guild access tiers and source-aware tool/compost requirements.
Passing CI **does not**
demonstrate that GUI events, in-game state transitions, bank visibility,
Shortest Path messages or client loading work in a real session.

In RuneLite, specifically check: default herb start; herb priority fallback;
disable Troll Stronghold then run Herbs, All Enabled and Herbs+Trees Merged;
disable only one allotment at Falador; save and reload a named merged preset;
leave and return to the active guide; save a current-run crop override; open a
bank and replan remaining; relog and resume; change characters; test Compost
Run; test with Shortest Path absent and installed. Confirm that the old
Advisor overlays, contracts, timers and compost trackers still function.

## Verified and approximate destinations

Herb, ordinary tree, fruit tree, calquat and hardwood tree coordinates come
directly from Quest Helper's existing Farm Run object steps. Grouped allotment,
seaweed, coral and grape locations are split into individual Quest Helper patch
identities instead of being mistaken for a single patch.

Where no exact Quest Helper tile is known, the Director can display a **region
centre as an approximate hint** or use a previously observed Advisor location.
It does **not** send Shortest Path to an unverified region centre. On known
shared farming areas it can route toward the nearby herb patch. More exact
single-patch waypoints and in-client confirmation remain needed for specialist
categories. Shortest Path must be installed separately for map navigation.

## Inventory waves and banking

v0.5 retains `DirectorWavePlanner` to build multiple loadouts without
losing future patches after a 28-slot boundary. It debits allocated planting
stock and potential protection payments between waves, so the same seed is
never promised to two stops. Unplannable patches are tracked separately and
are **never marked completed**. When another wave exists the Guide displays a
bank checkpoint instead of declaring the run finished.

`DirectorStockLedger` separates currently carried, last-observed
bank, and last-observed Seed Vault quantities. Expired caches are excluded.
The Guide shows estimated withdrawals for the first loadout; subsequent
loadouts should be recalculated after opening your bank/vault, because source
counts can change during farming.

`DirectorTeleportPlan` counts configured teleport needs per
bank-to-bank wave, consolidates consecutive visits to the same location,
counts consumable tablets over multiple waves, and excludes equipped
teleport items from the **inventory-slot estimate**. It warns if the observed
stock contains fewer travel items than the configured itinerary expects.
Charges on reusables and actual travel time remain unverified.

Bank waypoints currently include Farming Guild's accessible middle area
(45 Farming), Catherby, Falador west, Varrock west, and Ardougne north.
These are **approximate bank-area navigation targets**, not exact banker
interaction tiles. Auto-selection uses straight-line coordinates among
these five choices; it cannot evaluate teleports, restricted areas or
pathfinder travel times. Choose a bank manually if the suggestion is poor.

## Inventory and planning behavior

The existing Advisor crop database provides Farming level and planted quantity
requirements. Regular saplings (and other unstackable items) occupy separate
slots; stackable seed quantities use one slot. The 28-slot budget includes
per-wave tools, ordinary ultracompost slots or a filled bottomless bucket,
**per-wave** teleport allowances and configurable harvest space. Source-aware
withdrawals include tools and compost; bucket charges remain unknown. Optional max-stops-per-wave prevents an oversized loadout.
Replan after banking for additional waves of a large all-enabled run. It never
fabricates inaccessible bank/Seed Vault contents or silently consumes reserved
stock.

A ready patch without replacement stock can still appear as a **harvest-only**
stop; an unknown or empty patch is never treated as confirmed ready. Explicit
per-patch crop choices take priority over the overall crop order. The optional
Herblore preset prioritizes common potion-herb supplies; it is a preference
preset, not a claimed mathematical optimum. Optional nearby-stop sorting uses
**straight-line tile distance**, not obstacle-aware or teleport-aware route
optimisation.

## Limitations / in-game validation checklist

- In-game planting, harvesting, payments, diseased patch transitions and UI
  sizing are **not yet empirically verified** on this account.
- Some new access/quest gates are conservative. You may need to enable
  unverified locations or manually deselect locations you cannot reach.
- RuneLite's Time Tracking or Advisor must observe a patch before it can
  provide a trustworthy state. Unknown does not mean empty.
- Stock snapshots can become stale after bank withdrawals. Reopen storage
  before relying on exact available quantities.
- Banking-area navigation is now implemented through optional Shortest Path
  messages, but accurate nearest-bank travel times and bank-booth interaction
  have **not** been implemented. The complete original Quest Helper GUI and
  path-distance-based itinerary optimization also remain future work.
- Harvest yields can overflow the configured free-space reserve during a wave;
  the planner **cannot guarantee** the user's inventory will not fill. Bank
  early or replan as needed. No automatic deposits or withdrawals occur.
- The manifest checks *last-observed* inventory, bank, Seed Vault and equipped
  travel items. It does not guarantee a particular teleport's charges or
  that the external Shortest Path plugin will produce a route.
- No in-game action automation, macros, mouse control or botting is included.

CI checks `clean test build` on this branch. A green build proves source
compatibility with the CI RuneLite dependency, **not** flawless in-game play.

## Source and licenses

Farming Patch Advisor by Seadeadmeat (BSD-2-Clause):
https://github.com/Seadeadmeat/farming-patch-advisor

Quest Helper by Zoinkwiz and contributors (BSD-2-Clause):
https://github.com/Zoinkwiz/quest-helper

Original imported licenses and attribution headers are preserved as `LICENSE`
and `LICENSE-QUEST-HELPER`. Their names, UI and code ownership remain credited.
