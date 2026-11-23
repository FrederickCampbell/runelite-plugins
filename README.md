# Ironman Farming Director — v0.2.0-beta.1

An integration-first, standalone RuneLite farming companion for ironmen. This
is a **developer preview**: GitHub Actions compiles and runs tests successfully;
interactive RuneLite behavior still needs verification.

## Architecture

- **Farming Patch Advisor** remains the farming engine: crop and patch catalogs,
  selected/enabled locations, route/teleport preferences, observed inventory,
  bank and Seed Vault, timers, contracts, payments, compost and existing overlays.
- **Quest Helper farming code** is vendored into
  \`com.farmingpatchadvisor.queststate\`: \`FarmingWorld\`, \`FarmingRegion\`,
  \`FarmingPatch\`, \`PatchImplementation\`, \`PatchState\`, \`CropState\`,
  \`FarmingHandler\`, and \`PaymentTracker\`. Individual Quest Helper patch
  varbits and RuneLite Time Tracking predictions drive the guide's conditions.
- **The Guide UI** is a custom, compact Swing sidebar inspired by Quest Helper.
  It does **not** embed the entire original Quest Helper panel framework.
- **Shortest Path** remains a separate optional plugin. The Director sends the
  same \`shortestpath/path\` and \`shortestpath/clear\` event messages that Quest
  Helper uses; it never attempts to implement its own movement pathfinder.

## Installation via RuneLite Dev Manager

Download this branch:

    git clone --branch ironman-farming-director-v0.1 --single-branch https://github.com/FrederickCampbell/runelite-plugins.git Ironman-Farming-Director

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
   after closing bank/Seed Vault, and optional straight-line nearest-stop order.
4. Press **Start / Replan**. The plugin filters out known-growing patches and
   allocates available seeds/saplings by level, crop priority and reserved stock.
   A growing tree with protection still unconfirmed remains a protection stop.
5. Follow live instructions: harvest, check health, chop/remove, dig stump,
   cure, clear, plant, or protect. Auto-next advances after confirmed
   growing/protected states. No clicks or game actions are performed for you.
6. Use **Back**, **Next step**, **Pause**, **End**, or click a destination to
   override automatic routing. Use **Override this patch** to select a specific
   crop when available; **Clear override** restores automatic allocation.
7. Use **Replan remaining** after restocking to preserve completed patch IDs.
   **Resume saved run** restores a profile-scoped checkpoint after relogging.
   The bank-close auto-replan option is opt-in, and only triggers during an
   unfinished active run.
8. The **Advisor** tab still contains upstream timers, overlays, contracts,
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

## Inventory and planning behavior

The existing Advisor crop database provides Farming level and planted quantity
requirements. Regular saplings (and other unstackable items) occupy separate
slots; stackable seed quantities use one slot. The 28-slot budget includes
conservative tool/teleport allowances and configurable harvest space. Replan
after banking for additional waves of a large all-enabled run. It never
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
- Actual banking-stop navigation, a fully accurate itinerary solver using
  transport/teleports, and the complete original Quest Helper GUI/overlays
  remain future work. Large combined runs may require replan-and-bank waves.
- No in-game action automation, macros, mouse control or botting is included.

CI checks \`clean test build\` on this branch. A green build proves source
compatibility with the CI RuneLite dependency, **not** flawless in-game play.

## Source and licenses

Farming Patch Advisor by Seadeadmeat (BSD-2-Clause):
https://github.com/Seadeadmeat/farming-patch-advisor

Quest Helper by Zoinkwiz and contributors (BSD-2-Clause):
https://github.com/Zoinkwiz/quest-helper

Original imported licenses and attribution headers are preserved as \`LICENSE\`
and \`LICENSE-QUEST-HELPER\`. Their names, UI and code ownership remain credited.
