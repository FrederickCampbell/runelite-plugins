# Farming Helper v0.15.0-beta.1

A standalone Quest Helper-style farming guide. Choose a run, bring the items listed in the sidebar if desired, and follow the active patch instructions. Missing items never prevent navigation. The × or switching away from the sidebar ends the session and clears its route and highlights. Only crop preferences and enabled patches are saved, not the session.

- Automatic crop selection uses observed inventory, bank and Seed Vault stock; unknown/missing stock still produces an item requirement. Custom rankings take precedence over automatic fallback, and an individual eligible crop override is authoritative even when missing.
- Required and recommended items display live carried quantities. The bank and Seed Vault show outlines, not additional tabs or withdrawal automation. Storage counts are observed snapshots, not hidden queries.
- The guide uses the vendored farming registry, live varbits and RuneLite Time Tracking predictions, when available. It filters known growing, locked, disabled, completed and skipped patches. Unknown state remains inspectable.
- Optional Shortest Path integration uses active-step destinations only. Hespori, seaweed and coral include surface approach steps; manual cave/diving interactions are still required. Non-exact map markers are not asserted to be walkable object tiles. Ambiguous object groups receive instructions rather than misleading highlights.
- Preferences: compost, gardener removal/protection, ordered seed priorities, individual crop overrides and enabled patches. Combined runs use the same rules. There is no run persistence, loadout system, banking route, inventory cap or independent teleport planner.

## Development

Use JDK 21 and run `gradlew.bat clean test build` on Windows or `bash gradlew clean test build` on Linux/macOS. Java target remains 11. In RuneLite Dev Manager, refresh Source Files, select Farming Helper, build and launch. Live client validation is separate from CI.

Farming Patch Advisor by Seadeadmeat supplies original farming catalogs. Quest Helper by Zoinkwiz and contributors supplies the registry, decoder, herb/tree conditional steps and exact targets (original upstream commit b9b93706618a7b7fe7e5ea30e9a9ef35ba09673e). The sidebar follows Quest Helper's QuestOverviewPanel, QuestRequirementsPanel and QuestStepPanel structure. See LICENSE and LICENSE-QUEST-HELPER; vendored RuneLite decoder copyright notices are preserved. Special-area approach points are sourced from Shortest Path's `boats.tsv` and `transports.tsv`; see [upstream data](https://github.com/Skretzo/shortest-path/tree/master/src/main/resources/transports).
