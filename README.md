# Ironman Farming Director — v0.1

A standalone RuneLite plugin with a Quest Helper-style guided Farming sidebar powered by Farming Patch Advisor's existing codebase. The Advisor's crop catalogs, bank and Seed Vault scanning, timers, compost, protection payments, checklists, overlays and route editor are retained.

The new Guide tab adds ordered seed priorities, stock reserves, a 28-slot inventory budget, progress controls and optional Shortest Path destinations. The existing Advisor is available in its own tab.

## Install with RuneLite Dev Manager

Download the ironman-farming-director-v0.1 branch or clone it:

    git clone --branch ironman-farming-director-v0.1 --single-branch https://github.com/FrederickCampbell/runelite-plugins.git Ironman-Farming-Director

Move the Ironman-Farming-Director project folder into:

    C:\_Hub\Projects\Gaming\OSRS\RuneLite\Plugins\Ironman-Farming-Director\

Open your installed RuneLite Dev Manager, click Refresh, select Ironman-Farming-Director, click Build and then Launch. Do not overwrite any other project or modify Dev Manager.

This is a standard Gradle project targeting Java 11. The RuneLite developer build uses Gradle and downloads its dependencies on first run. The local Dev Manager has NOT been accessible or verified from this session.

## How to use the Guide

1. Open your bank and Farming Guild Seed Vault after logging in. The plugin never reads unopened storage. A snapshot older than six hours is treated as stale and is excluded from the plan.
2. Open the Ironman Farming Director navigation button. Select HERB or TREE.
3. Choose the Highest Available mode or enable Use custom priority. Enter ordered crop names such as Ranarr, Snapdragon, Toadflax, Irit, Avantoe.
4. Enter reserves such as Ranarr=10, Snapdragon=3. The planner will preserve those quantities and allocate another crop when necessary.
5. Select tree protection payments if desired and press Start / Replan.
6. Follow numbered steps and use Next step, Back, Pause and End. If Shortest Path is installed, path requests target the current patch. If absent, you still have the named destination and coordinates.
7. Use the Advisor tab for the original timers, custom routes and teleport choices, checklists and overlays.

Each character has separate stored Guide priorities (RuneLite RS-profile configuration). A distinct ironman-farming-director configuration group prevents clashing with the original Farming Patch Advisor.

## Implemented constraints

- Guided runs currently cover herbs and regular trees.
- Uses stock quantities from observed inventory, fresh bank and fresh Seed Vault; missing storage yields visible warnings.
- Regular-tree crops are plantable saplings, not raw seeds; raw seeds trigger a preparation warning when no suitable saplings are available.
- Custom priorities, insufficient stock, reserved stock and Farming-level limits are handled by an explicit allocation algorithm.
- Planned seeds, unstackable saplings, payment items and configurable harvest reserve are counted against 28 inventory slots. Impossible full runs are shortened, with a banking warning.
- Basic quest checks cover Morytania, Troll Stronghold and Weiss. Farming Guild has separate level gates (65 for both regular trees and herbs). Some new/unverified areas are opt-in, never silently assumed accessible.
- Advisor's route order can be edited in its Route & Teleports control; no global optimization in v0.1.
- Protection-payment requirements may be missing from available stock, and the sidebar warns.
- No game actions are automated. Manual step completion is intentional for first validation.

## Build and test

    gradlew.bat clean test build

Optional developer harness:

    gradlew.bat run

The existing Dev Manager is the normal route for testing. Tests include priority fallback, reserve quantities, insufficient seeds, level eligibility, raw tree seed vs sapling handling and inventory-slot capacity. A real RuneLite account is still needed to verify live patch states, storage events, profile switching, sidebar geometry and Shortest Path rendering.

## Source and attribution

Primary upstream: Seadeadmeat / Farming Patch Advisor, BSD-2-Clause:
https://github.com/Seadeadmeat/farming-patch-advisor

The original source (snapshot tree fb0aa6115dd0a7dc71b146b42870b7c19b1ca10d) is included with modifications. Original LICENSE is retained.

Quest Helper: Zoinkwiz, BSD-2-Clause:
https://github.com/Zoinkwiz/quest-helper

The guide draws on Quest Helper's step-oriented sidebar patterns, farm-run waypoints and its public Shortest Path message mechanism. It does not import Quest Helper private implementation classes. Its license is included as LICENSE-QUEST-HELPER.

## Remaining verification and next milestone

This branch is an engineering build, not a declared production-ready plugin until both automated tests and in-game checks pass. After verification, add reliable automatic patch progression, bank-stop optimization, fruit/hardwood trees and allotments using the existing Advisor catalogs.
