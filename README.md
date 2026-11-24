# Farming Helper v0.16.0-beta.1

A standalone Quest Helper-style farming guide. Choose a run, bring the items listed in the sidebar if desired, and follow the active patch instructions. Missing items never prevent navigation. The × or switching away from the sidebar ends the session and clears its route and highlights. Crop preferences, enabled patches and last observed storage contents are saved per character; no run progress is saved.

- Automatic crop selection uses observed inventory, bank and Seed Vault stock; unknown/missing stock still produces an item requirement. Custom rankings take precedence over automatic fallback, and individual preferences reserve available stock and fall through to category priorities and then the highest eligible crop. Missing-stock fallback is advisory.
- Required and recommended items display live carried quantities. The bank and Seed Vault show outlines, not additional tabs or withdrawal automation. Storage counts are observed snapshots, not hidden queries.
- The guide uses the vendored farming registry, live varbits and RuneLite Time Tracking predictions, when available. It filters known growing, locked, disabled, completed and skipped patches. Unknown state remains inspectable.
- Optional Shortest Path integration uses active-leg destinations only. Modern versions support reachability queries, bounded nearby fallback, and route-ownership checks; legacy versions receive normal path messages. Hespori, seaweed and coral include explicit approach/exit transitions; Anglers’ Retreat recognizes the rowboat unlock and mooring stages. Manual interactions remain required. Local object transform varbits, canonical regions and Advisor-style connected groups identify actual patch objects; local collision data supplies perimeter candidates. Non-exact map markers are not asserted to be walkable object tiles. Ambiguous object groups receive instructions rather than misleading highlights.
- Preferences: compost, gardener removal/protection, ordered seed priorities, individual crop overrides and enabled patches. Combined runs use the same rules. There is no run persistence, loadout system, banking route, inventory cap or independent teleport planner.

## Development

Use JDK 21 and run `gradlew.bat clean test build` on Windows or `bash gradlew clean test build` on Linux/macOS. Java target remains 11. In RuneLite Dev Manager, refresh Source Files, select Farming Helper, build and launch. Live client validation is separate from CI.

Farming Patch Advisor by Seadeadmeat supplies original farming catalogs. Quest Helper by Zoinkwiz and contributors supplies the registry, decoder, herb/tree conditional steps and exact targets (original upstream commit b9b93706618a7b7fe7e5ea30e9a9ef35ba09673e). The sidebar follows Quest Helper's QuestOverviewPanel, QuestRequirementsPanel and QuestStepPanel structure. See LICENSE and LICENSE-QUEST-HELPER; vendored RuneLite decoder copyright notices are preserved. Special-area approach points are sourced from Shortest Path's `boats.tsv` and `transports.tsv`; see [upstream data](https://github.com/Skretzo/shortest-path/tree/master/src/main/resources/transports).

## v0.16 validation scope

The automated tests exercise allocation, snapshots, route-message lifecycle, transport stages and crop state decoding. In-game path reachability depends on Shortest Path’s installed version, unlocked transports, collision data and player location. Exact unresolved object identities are not invented; use live highlighting where matched. The route bridge does not automate gameplay or add a new pathfinder.
