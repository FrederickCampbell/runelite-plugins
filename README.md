# Ironman Farming Director — v0.3.0-beta.1

An integration-first, standalone RuneLite farming companion for ironmen. This
is a **developer preview**: GitHub Actions compiles and runs tests successfully;
interactive RuneLite behavior still needs verification.

## Architecture

- **Farming Patch Advisor** remains the farming engine: crop and patch catalogs,
  selected/enabled locations, route/teleport preferences, observed inventory,
  bank and Seed Vault, timers, contracts, payments, compost and existing overlays.
- **Quest Helper farming code** is vendored into
  `com.farmingpatchadvisor.queststate`: `FarmingWorld`, `FarmingRegion`,
  `FarmingPatch`, `PatchImplementation`, `PatchState`, `CropState`,
  `FarmingHandler`, and `PaymentTracker`. Individual Quest Helper patch
  varbits and RuneLite Time Tracking predictions drive the guide's conditions.
- **The Guide UI** is a custom, compact Swing sidebar inspired by Quest Helper.
  It does **not** embed the entire original Quest Helper panel framework.
- **Shortest Path** remains a separate optional plugin. The Director sends the
  same `shortestpath/path` and `shortestpath/clear` event messages that Quest
  Helper uses; it never attempts to implement its own movement pathfinder.

## Installation via RuneLite Dev Manager

Download this branch:

    git clone --branch ironman-farming-director-v0.3 --single-branch https://github.com/FrederickCampbell/runelite-plugins.git Ironman-Farming-Director

Place the Gradle project at:

    C:\_Hub\Projects\Gaming\OSRS\RuneLite\Plugins\Ironman-Farming-Director\

In RuneLite Dev Manager choose **Refresh → Build → Launch**. The installed Dev
Manager and user's Windows client are not accessible from this environment.

Or build from the project root:

    gradlew.bat clean test build

## Using the Guide

1. Log into your intended character and **open your bank and Seed Vault** to
   scan stock. Stored inventories are profile-local, only observed rather than
   inferred, and are excluded when older than six hours.
2. Choose a run: herbs, allotments, flowers, trees, fruit trees/calquats,
   hardwoods, hops, bushes, cactus, specialist patches, core mixed farm run,
   all tree categories, or **all enabled patches**.
3. Open **Planner options** to choose crop priorities, stock reserves, gardener
   payments, Herblore-supplies preset, automatic next step, automatic replanning
   after closing bank/Seed Vault, optional straight-line nearest-stop order,
   **maximum patch stops per loadout** (1–28; initially 8), and a preferred
   banking destination. You can leave banking on nearest listed bank.
4. Press **Start / Replan**. The plugin filters out known-growing patches and
   allocates available seeds/saplings by level, crop priority and reserved stock.
   A growing tree with protection still unconfirmed remains a protection stop.
5. Follow live instructions: harvest, check health, chop/remove, dig stump,
   cure, clear, plant, or protect. Auto-next advances after confirmed
   growing/protected states. No clicks or game actions are performed for you.
6. Use **Back**, **Next step**, **Pause**, **End**, or click a destination to
   override automatic routing. Use **Override this patch** to select a specific
   crop when available; **Clear override** restores automatic allocation.
7. The entire itinerary is split into **inventory waves** using the same
   Advisor-derived crops, stock, protection payments and RuneLite item
   stackability as the individual-run planner. Before a wave, see the required
   seeds/saplings, possible gardener payments, configured travel items, and
   estimated bank/Seed Vault withdrawals from the latest observations.
8. After the last patch of a wave, the guide enters an **explicit bank
   checkpoint**, suggests a nearby *listed* bank area (or your manual choice),
   and sends an optional Shortest Path waypoint. **Next wave** continues
   manually after restocking. No banking or item-withdrawal clicks are automated.
9. Use **Replan remaining** after restocking to preserve completed patch IDs.
   **Resume saved run** restores a profile-scoped checkpoint after relogging.
   The bank-close auto-replan option is opt-in, and only triggers during an
   unfinished active run.
10. The **Advisor** tab still contains upstream timers, overlays, contracts,
    custom route choices, checklists and their original controls.

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

v0.3 uses `DirectorWavePlanner` to build multiple loadouts without
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
conservative tool and **per-wave** teleport allowances and configurable
harvest space. Optional max-stops-per-wave prevents an oversized loadout.
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
