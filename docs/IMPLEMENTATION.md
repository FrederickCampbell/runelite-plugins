# Development

Build with JDK 21. Gradle targets Java 11. Run `gradlew.bat clean test build` on Windows or `bash gradlew clean test build` on Linux and macOS. The JAR is under `build/libs`. The test suite also renders a 225-pixel guide preview in `build/reports/farming-helper-preview.png`.

For RuneLite Dev Manager, install the source directory under `Plugins/Farming-Helper`, refresh **Source Files**, select **Farming Helper**, then build and launch. From a checkout, `gradlew run` starts a developer client.

## Runtime behavior

- The inherited farming registry identifies named plots and their decoder varbits. Exact object IDs or verified object groups identify the local target. Kastori flowers and compost bins additionally require a matching live transform and object footprint. Time Tracking and saved observations support remote planning.
- The run retains its ordered itinerary and tracks completion evidence separately from pending, deferred, skipped and preserved outcomes. Player settings belong to the RuneScape profile. Run overrides expire on ending the run or changing character.
- RuneLite container events invalidate inventory, equipment and seed-box totals. The seed-box tracker reconciles native snapshots, confirmed inventory transfer deltas and game messages. Last-known quantities are saved per character but require confirmation after a session change; direct planting is recommended only when the native order is known. The helper records the bank and Seed Vault while their native interfaces are open. Physical requirements, preparatory tree materials, worn-item bank display aliases and optional teleports have distinct quantities.
- Shortest Path owns pathfinding. Farming Helper requests a single current leg through plugin messages. Bank Tags provides the optional farming shelf. The settings screen lists available integrations. See [review and live-client checks](../PLUGIN_HUB_REVIEW.md).

## Attribution

Seed-box container tracking was researched against the [Seed Box plugin](https://github.com/fgillet4/osrs-seedbox-plugin) (native container ID and item variants); Farming Helper uses its own ordered snapshot and existing stock model. Closed-box transfer confirmation and game-message formats were researched against [Atlas](https://github.com/Phase-UnPhasable/atlas); the implementation is independent.

| Upstream | Use and provenance |
| --- | --- |
| [Farming Patch Advisor](https://github.com/Seadeadmeat/farming-patch-advisor) | Original farming catalogs and connected-object highlighting by Seadeadmeat. See the [project license](../LICENSE). |
| [Quest Helper](https://github.com/Zoinkwiz/quest-helper/tree/b9b93706618a7b7fe7e5ea30e9a9ef35ba09673e) | Farming registry and decoder, herb and tree conditions, exact targets and instance coordinate handling. Sidebar interaction draws on Quest Overview, Requirements and Step panels. See [LICENSE-QUEST-HELPER](../LICENSE-QUEST-HELPER). |
| [RuneLite](https://github.com/runelite/runelite) | Farming decoder and map markers, native item metadata and Bank Tags layout techniques. Original source notices include Abex and NotFoxtrot. See [LICENSE-BANK-TAGS](../LICENSE-BANK-TAGS). |
| [Shortest Path](https://github.com/Skretzo/shortest-path/tree/master/src/main/resources/transports) | Transport destinations and requirements, boat approaches and POH coordinates. See [LICENSE-SHORTEST-PATH](../LICENSE-SHORTEST-PATH). |
| [Farm Run Autopilot](https://github.com/elzerma/farm-run-autopilot) | Historical route-planning work. See [LICENSE-FARM-RUN-AUTOPILOT](../LICENSE-FARM-RUN-AUTOPILOT). Codex Rounds inspired place grouping and Better Farming inspired itinerary pinning. |
| [Birdhouse Overlay](https://github.com/hong-niu/runelite-birdhouse-overlay) | Birdhouse observations and timing. See [LICENSE-BIRDHOUSE](../LICENSE-BIRDHOUSE). |
| [Old School RuneScape Wiki](https://oldschool.runescape.wiki/w/Farming_runs) | Crop XP tables, patch locations and reference travel options. Variable yields in crop rankings use the documented high-level estimates. |

Storage reconciliation (v1.07.2): Native storage snapshots must decode completely before replacing remembered counts. Positive quantities and native placeholders are independent; timestamp keys are profile-local and distinguish an unobserved container from a verified empty one. Remembered quantities remain advisory. Closed-box observations are action-correlated, bounded and may be invalidated when ambiguous; native snapshots supersede inference. Ordinary menu clicks do not cancel pending transfers. Vault projection is fail-safe per section and rechecks native child identities after rebuilds.
