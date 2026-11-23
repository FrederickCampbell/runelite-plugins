# Ironman Farming Director — v0.1 (Quest Helper state integration)

A standalone RuneLite plugin with a Quest Helper-style guided Farming sidebar powered by Farming Patch Advisor's existing codebase. The Advisor's crop catalogs, bank and Seed Vault scanning, timers, compost, protection payments, checklists, overlays and route editor are retained.

The new Guide tab adds ordered seed priorities, stock reserves, a 28-slot inventory budget, dynamic patch-state instructions, automatic step advancement, and optional Shortest Path destinations. It reuses Quest Helper's farming-region registry, patch-varbit decoder and Time Tracking predictions; these sources are vendored under the separate `com.farmingpatchadvisor.queststate` package. The existing Advisor is available in its own tab.

## Install with RuneLite Dev Manager

Download the ironman-farming-director-v0.1 branch or clone it:

    git clone --branch ironman-farming-director-v0.1 --single-branch https://github.com/FrederickCampbell/runelite-plugins.git Ironman-Farming-Director

Move the Ironman-Farming-Director project folder into:

    C:\_Hub\Projects\Gaming\OSRS\RuneLite\Plugins\Ironman-Farming-Director\

Open your installed RuneLite Dev Manager, click Refresh, select Ironman-Farming-Director, click Build and then Launch. Do not overwrite any other project or modify Dev Manager.

This is a standard Gradle project targeting Java 11. The RuneLite developer build uses Gradle and downloads its dependencies on first run. The local Dev Manager has NOT been accessible or verified from this session.

## How to use the Guide

1. Open your bank and Farming Guild Seed Vault after logging in. The plugin never reads unopened storage. A snapshot older than six hours is treated as stale and is excluded from the plan.
2. Open the Ironman Farming Director navigation button. Select HERB, TREE or ALLOTMENT.
3. Choose the Highest Available mode or enable Use custom priority. Enter ordered crop names such as Ranarr, Snapdragon, Toadflax, Irit, Avantoe.
4. Enter reserves such as Ranarr=10, Snapdragon=3. The planner will preserve those quantities and allocate another crop when necessary.
5. Select tree protection payments if desired and press Start / Replan.
6. Follow the currently highlighted action: harvest, check tree health, remove tree, dig stump, cure disease, clear a dead crop, plant or protect the tree. If the patch becomes confirmed growing (and optional gardener protection is confirmed), **Auto-next** proceeds to the next destination. Turn Auto-next off or use Back, Next step, Pause, End, or click a patch name for manual control.
7. Quest Helper's varbit decoder reads live patches near you. Elsewhere it uses RuneLite Time Tracking observations, with Advisor's timers as a fallback. If the patch has never been observed, it is **unknown**, not treated as empty. Visit/inspect the patch or use manual Next.
8. If Shortest Path is installed, the Guide sends it the target destination, otherwise the patch name and coordinates remain visible.
9. Use the Advisor tab for the original timers, custom routes and teleport choices, checklists and overlays.

Each character has separate stored Guide priorities (RuneLite RS-profile configuration). A distinct ironman-farming-director configuration group prevents clashing with the original Farming Patch Advisor.

## Implemented constraints

- Guided runs cover herbs, regular trees, and independent allotment patches, using existing Advisor crop data and Quest Helper's individual patch varbits. The original Advisor has additional run categories.
- Uses stock quantities from observed inventory, fresh bank and fresh Seed Vault; missing storage yields visible warnings.
- Regular-tree crops are plantable saplings, not raw seeds; raw seeds trigger a preparation warning when no suitable saplings are available.
- Custom priorities, insufficient stock, reserved stock and Farming-level limits are handled by an explicit allocation algorithm.
- Planned seeds, unstackable saplings, payment items and configurable harvest reserve are counted against 28 inventory slots. Impossible full runs are shortened, with a banking warning.
- Basic quest checks cover Morytania, Troll Stronghold and Weiss. Farming Guild requires level 65 for regular trees/herbs and level 45 for allotments. Some new/unverified areas are opt-in, never silently assumed accessible.
- Advisor's route order can be edited in its Route & Teleports control; no global optimization in v0.1. Already-growing patches with known states are omitted from new planting requirements.
- Protection-payment requirements may be missing from available stock, and the sidebar warns.
- The Guide auto-advances on observed growing/protected states, following the Quest Helper conditional-step approach. It never clicks in-game objects, banks, harvests or plants for you. Manual completion remains available as a fallback.

## Build and test

    gradlew.bat clean test build

Optional developer harness:

    gradlew.bat run

The existing Dev Manager is the normal route for testing. Tests include priority fallback, reserve quantities, insufficient seeds, level eligibility, raw tree seed vs sapling handling, inventory-slot capacity, actual Quest Helper patch-registry lookups, independent allotment patches and state-driven transitions. Growing patches are excluded from new planting requirements, while existing trees that still require protection can appear without claiming another sapling. A real RuneLite account is still needed to verify live patch states, storage events, profile switching, sidebar geometry and Shortest Path rendering.

## Source and attribution

Primary upstream: Seadeadmeat / Farming Patch Advisor, BSD-2-Clause:
https://github.com/Seadeadmeat/farming-patch-advisor

The original source (snapshot tree fb0aa6115dd0a7dc71b146b42870b7c19b1ca10d) is included with modifications. Original LICENSE is retained.

Quest Helper: Zoinkwiz, BSD-2-Clause:
https://github.com/Zoinkwiz/quest-helper

The guide ports Quest Helper's actual `FarmingWorld`, `FarmingPatch`, `FarmingRegion`, `PatchImplementation`, `PatchState`, `CropState`, `FarmingHandler` and `PaymentTracker` classes into a standalone namespace, retaining their original license headers. The visible sidebar remains a streamlined standalone Swing implementation (not the entire Quest Helper plugin). The Shortest Path event-message contract is reused directly. Quest Helper is **not required to be installed**. Its license is included as LICENSE-QUEST-HELPER.

## Remaining verification and next milestone

This branch is an engineering build, not a declared production-ready plugin until both automated tests and in-game checks pass. After verification, improve exact allotment-tile waypoints, bank-stop optimization, fruit/hardwood trees and additional crop runs with the already imported Advisor catalogs. Current allotment navigation targets the known nearby farm area rather than the precise subpatch tile; the sidebar labels each subpatch independently.
