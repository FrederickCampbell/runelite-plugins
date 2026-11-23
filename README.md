# Farming Helper — v0.9.0-beta.1

**Basically, Quest Helper, for farm runs.**

A standalone RuneLite farming assistant, designed for ironmen and useful for any account. **This is
a developer beta, not an in-game verified production release.** The consolidated default branch
preserves Advisor's crop, patch, compost, stock and banking infrastructure and
the vendored Quest Helper farming-state reader, with the unified RuneLite-native multi-page sidebar introduced in v0.4.

## v0.9 — UI and architecture refactor

Modern component-local styles, compact semantic rows and dark controls. The single patch-type preference store replaces legacy run-priority keys. Set crop priorities again after upgrading. Diagnostics are optional and local. A development-only headless Swing component preview is generated at `build/reports/farming-helper-preview.png` by the tests. This is not a live client screenshot.

## Architecture

- **Farming Patch Advisor** is the backend for crops, patches, growth timers,
  contracts, compost, payments, teleports, bank and Seed Vault observations.
  Its original panel is no longer a visible tab; the implementations are retained, but legacy overlays are no longer registered. Some specialized legacy panel controls still
  remain accessible through RuneLite plugin configuration; bank filtering and item outlines are restricted to preparation or active interactions.
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

[Download the v0.9 beta from GitHub Releases](https://github.com/FrederickCampbell/runelite-plugins/releases/tag/v0.9.0-beta.1) — choose **Source code (zip)**.

The release is not a Plugin Hub listing; it is intended for the local development manager.

Extract its Gradle project into:

    C:\_Hub\Projects\Gaming\OSRS\RuneLite\Plugins\Farming-Helper\

Use **Refresh → Build → Launch** in RuneLite Dev Manager. A normal local build
from the project root uses `gradlew.bat clean test build`. CI uses Java 21 to
run Gradle while the source targets Java 11. Neither the Windows launch
workflow nor in-game behavior has been tested by this CI.

## v0.8.1 — Patch Settings

- Home run buttons start runs directly; combined runs retain their membership preset selector.
- Global Settings controls run behavior. Patch Settings controls each physical patch type, with Automatic or custom progressive ranked dropdowns, reserves, route editing and collapsible farming locations.
- Individual and combined runs inherit the same physical patch-type preferences. Legacy category preferences are filtered into each physical type until explicitly edited. Historical merged-only priority overrides remain stored but are no longer applied; configure the shared patch type instead.
- Access and planting requirements are shown separately (Anima: guild tier 65, seeds 76).

## v0.8 — Polish Update

- Forest-green sidebar with locally styled Swing controls and OSRS item sprites.
- Run cards, settings groups and patch categories/locations start collapsed. Expand a run to configure and start it.
- One settings cog; routine preferences save on change. Explicit route saves remain for multi-step edits.
- Global/category/location/individual patch enable and disable controls, independent of unlock checks. Bulk controls include filtered-out patches in the stated scope.
- Legacy Advisor overlay registrations and recommendation fallbacks are removed. Only active Quest Helper-style hints and required bank/vault item outlines/filter during preparation/restocking remain. Tracking, timers, contracts and planning remain.
- New runs begin at the supply checklist. Default stop cap is 28; the existing inventory budget determines wave sizes. Explicit previously saved limits remain intact.
- Existing configuration and account preferences are unchanged.

## v0.7 interaction guidance

Tree removal and tree protection are independent. Pay 200 coins to the
gardener or chop/dig yourself; the appropriate interaction is selected
from current patch state. Protection payments remain conditional on
planned available stock. Choose no compost, regular compost, supercompost,
ultracompost or the filled bottomless bucket (ultracompost fallback).
The loadout accounts for the selected compost and removal coins. Verify
bottomless contents and charges yourself.

The exact 22 tree-run and 10 herb-run object definitions from Quest Helper
are used first, with original gardener IDs for tree removal/protection.
Other patches retain the existing conservative identification logic.
The current interaction drives the world, sidebar and inventory highlights;
during travel, configured route items and their supported visible spellbook
alternatives are highlighted instead. Shortest Path remains optional.
The dialogue overlay outlines the known, specific gardener choices without
changing game widgets or clicking; generic Yes confirmations are manual.
The current patch card shows observed interaction transitions, not guessed
completion. Rake, plant, compost, payment and tree removal are separate
instructions. No changes to game input or menu behavior.

## Using the sidebar

**Default workflow:** choose a run → withdraw missing supplies (live red/green
counts) → Begin farming → follow Shortest Path → click the highlighted patch
or gardener. The guide advances after observed work; Next is a manual fallback.
Only the active farming target is marked in the world. It uses the existing
Advisor event-tracked patch objects and patch footprint renderer, with the
clickbox, downward arrow, active inventory-item highlight and required-item icon pattern from Quest Helper's
`ObjectStep` / `QuestHelperWorldOverlay`. No dependency on Quest Helper's
plugin runtime, no automated game actions. World guidance can be disabled
or recolored in RuneLite's Director configuration. Exact coordinates and
unambiguous loaded patch identity are required; ambiguous numbered grape
rows and unavailable scene objects do not get guessed highlights.
The gardener NPC is selected using the vendored Quest Helper farming registry.
Newly planted crops with observed work and compost enabled wait for compost
confirmation from Advisor's care tracker before auto-next; manual Next remains
available when tracking is incomplete. The legacy timer overlay is disabled; use Timers / contracts for additional information.

The default guide shows the current destination, short action, progress and
next two stops; More options & details reveals the diagnostic/advanced controls.
Default merged runs visit each farm's patches together unless a saved custom
order or nearest-stop option applies. All Enabled is grouped by location unless a custom order is saved. This is stable grouping, not optimal travel-time pathfinding.

1. Log into a character and scan your **bank** and **Farming Guild Seed Vault**
   so the Director can use observed stock. Stale snapshots are not presented
   as live stock. Choose **Herb Run** (default) and press **Start farming run**.
2. The **Active Guide** shows a highlighted current destination, action,
   progress bar, upcoming stops, and Back / Next / Pause / End controls.
   Use the pencil to override a crop temporarily or permanently. Open
   **View loadout & withdrawals** for a separate item-by-item supply page
   (carried at planning, bank, Seed Vault, and missing). Expand planning
   details for notices. **Navigation…** reissues the route or lets you save
   your current tile when standing in the selected patch's known region;
   player waypoints are explicitly labeled and never used as state evidence.
3. The **Run Editor** has a visual crop priority editor: add a crop, move
   it up/down, remove it, add remaining crops, or reserve a quantity of stock.
   Automatic mode still picks the highest eligible available crop; custom
   mode allows only listed crops, first eligible and available wins. The
   existing CSV profile format is preserved. For merged runs select a named
   preset on the home screen, check categories in the editor, and save.
   Per-category priority and reserve overrides are available; delete unused
   presets or restore omitted route stops without resetting the entire route.
   The **Route Editor** reorders, skips, restores and assigns travel items to
   grouped stops. Global individual patch inclusion still takes precedence.
   Merged presets retain separate routes and inherit per-category crop choices
   and stock reserves. All Enabled also inherits them in automatic mode.
4. The **Patch Manager** shows individual known patches grouped by type and
   location. Search, expand/collapse categories, toggle individual patches or
   use location/category bulk controls. Disabled means **excluded globally**
   from regular, all-enabled and merged plans. Unknown patch state is marked
   unverified rather than assumed ready. The visible unlock description
   intentionally reports only known requirements.
5. **Global Settings** contains auto-next, ready-only filtering, automatic
   replan after bank closure, approximate nearest-stop order, gardener payment
   preference, unverified-location inclusion, inventory-wave limit and bank
   choice, compost preference, adjustable harvest-space reserve (-1 uses the
   run default), and an explicit Shortest Path switch. Editing these pages
   never ends an active Guide; starting a replacement requires confirmation.
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

## What changed in v0.6

- Narrow, RuneLite-native layout: width-tracking scroll views, wrapped long
  labels, consistent compact controls, visible active-destination card,
  progress indicator, completed-state accents and distinct loadout page.
- Native visual crop ordering and reserve editor using the existing crop
  catalog. Account settings remain backward compatible; no second database.
- Home-screen merged preset chooser, preset deletion with cleanup, per-preset
  reserves, restore-omitted-stop action, patch counts, attention filtering and
  more readable global patch group controls.
- The bank's Farm Run filter, bank checklist, in-game checklist and item
  outlines use the active Director loadout rather than unrelated legacy
  selections. Completed stops no longer contribute planting stock; the
  next wave is shown at a bank checkpoint. Travel and shared tools can remain
  conservative estimates. The existing Advisor fallback and separate contract
  checklist remain available when no Director run is active.
- Per-wave consumable travel slot cost uses actual item stackability; equipment
  does not occupy bag space for reusable configured teleports.
- Tree protection is planned when observed supplies and solo-wave capacity allow
  it. Otherwise the preferred planting crop remains selected and the guide
  reports an omitted payment, rather than blocking a magic sapling with an
  impossible coconut loadout. Checked trees without a replacement are not
  directed to be chopped. Fruit and bark can be collected manually.
- Player-saved per-patch navigation waypoints supplement verified Quest Helper
  and observed Advisor targets. Only the matching known farming area may be
  saved; custom waypoints are not verified exact patch locations or evidence
  of crop state. Shortest Path remains an optional independent plugin.
- Exposed compost preference and harvest-space reserve in the sidebar. Compost
  itinerary excludes known inaccessible areas. Added new regression tests for
  ranked-list persistence, loadout/teleport budgets, waypoint parsing,
  viewport sizing policy, safe tree handling and bank checklist projection.

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

## Regression and playtest checklist

GitHub Actions runs `bash gradlew clean test build --stacktrace`. The tests
cover patch identity splitting, global exclusion semantics, merged categories,
category-specific crop priorities, per-profile saved choices, and preserving
the active run mode while viewing another mode. New tests exercise catalog
crosswalks, special state normalization, inventory capacity, progress after
manual jumps, guild access tiers, source-aware tool/compost requirements,
visual priority-model persistence, viewport sizing policy, optional gardener
payments, waypoint parsing and the active-bank checklist projection.
Passing CI **does not**
demonstrate that GUI events, in-game state transitions, bank visibility,
Shortest Path messages or client loading work in a real session.

In RuneLite, specifically check: default herb start; herb priority fallback;
disable Troll Stronghold then run Herbs, All Enabled and Herbs+Trees Merged;
disable only one allotment at Falador; save and reload a named merged preset;
leave and return to the active guide; save a current-run crop override; open a
bank and replan remaining; relog and resume; change characters; test Compost
Run; test with Shortest Path absent and installed. Also test moving and
reserving Ranarr/Snapdragon in the visual editor; verify the active wave's
bank filter and outlines, switch to a narrow/short sidebar, restore an omitted
stop, save a player waypoint for a specialist patch, delete/recreate a preset,
and check that magic saplings are not blocked by oversized payment needs.
Confirm that the old
Advisor overlays, contracts, timers and compost trackers still function.

## Verified and approximate destinations

Herb, ordinary tree, fruit tree, calquat and hardwood tree coordinates come
directly from Quest Helper's existing Farm Run object steps. Grouped allotment,
seaweed, coral and grape locations are split into individual Quest Helper patch
identities instead of being mistaken for a single patch.

Where no exact Quest Helper tile is known, the Director can display a **region
centre as an approximate hint** or use a previously observed Advisor location.
It does **not** send Shortest Path to an unverified region centre. A user may
explicitly save a personal waypoint in the matching patch area. On known
shared farming areas it can route toward the nearby herb patch. More exact
single-patch waypoints and in-client confirmation remain needed for specialist
categories. Shortest Path must be installed separately for map navigation.

## Inventory waves and banking

v0.6 retains `DirectorWavePlanner` to build multiple loadouts without
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

CI checks `clean test build` on the default branch before release publication. A green build proves source
compatibility with the CI RuneLite dependency, **not** flawless in-game play.

## Source and licenses

Farming Patch Advisor by Seadeadmeat (BSD-2-Clause):
https://github.com/Seadeadmeat/farming-patch-advisor

Quest Helper by Zoinkwiz and contributors (BSD-2-Clause):
https://github.com/Zoinkwiz/quest-helper

Original imported licenses and attribution headers are preserved as `LICENSE`
and `LICENSE-QUEST-HELPER`. Their names, UI and code ownership remain credited.
