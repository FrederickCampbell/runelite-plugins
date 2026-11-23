# Farming Helper — v0.10.0-beta.1

**Basically, Quest Helper, for farm runs.**

A standalone RuneLite farming assistant designed for ironmen and useful for any account. This is a developer beta, not a Plugin Hub listing or an in-game verified production release.

## Use

1. Log in and open your bank and Seed Vault to observe supplies.
2. In Patch Settings, choose Automatic or rank crops separately for each patch type. Enable the locations you want. Access and planting requirements are independent.
3. In Global Settings, choose compost, gardener payments, guidance and banking preferences.
4. Start a run to open its live guide. Gather missing supplies from the inline checklist; when ready, guidance requests navigation to the first verified destination. You may explicitly continue with missing items. All in-game actions remain manual.
5. Pause or finish to clear the active guide. Timers and contracts remain accessible in the sidebar.

The plugin is read-only guidance: every in-game action remains manual. Unknown patch observations and access are not treated as proven. Optional nearest-stop routing is a heuristic.

## v0.10

- Complete itineraries have no arbitrary stop cap; inventory slot budget and available stock determine loadouts.
- Inline live supply checklist, auto-start once carried supplies are ready, completed/upcoming patch rows, and remaining travel requirements.
- Quest Helper verified destinations or saved player waypoints drive Shortest Path requests; legacy timers remain a state observation source, not a route source.
- Existing crop preferences and settings are preserved. This is an unverified development beta until build and in-client checks pass.

## v0.9

- Compact neutral-dark UI, green accents, uniform system typography, semantic controls and normalized item sprites.
- Component-local FlatLaf styling where supported; local fallback controls otherwise. No new GUI dependency or global look-and-feel change.
- Collapsed filters and advanced crop options, automatic patch refresh while the settings page is visible, and scroll position restoration.
- One crop preference store, a renamed `com.farminghelper` package and `farming-helper` configuration group. **This prerelease resets Farming Helper settings and saved runs.** Previous configurations are not deleted; they remain under the old group.
- Optional local diagnostics under Global Settings → Advanced. No account identifiers or network upload.
- A headless Swing component preview is generated during tests at `build/reports/farming-helper-preview.png`. This is a component test, not a live RuneLite screenshot.

## Development and install

Use a validated source commit or a published release. Do not install a development branch as a verified release.

Extract the Gradle project to the Farming-Helper directory in RuneLite Dev Manager. Refresh → Build → Launch. Run `gradlew.bat clean test build` (Windows) or `bash gradlew clean test build` (Linux/macOS). Java 11 source target; CI uses JDK 21.

## Architecture

- `FarmingHelperPlugin`: lifecycle, game events and stock observations.
- `FarmingHelperPanel`: sidebar navigation and guide coordination.
- `FarmingHelperTheme` and `FarmingHelperComponents`: local styling and reusable UI primitives.
- `PatchPriorityEditor`: independent crop preference UI with explicit controller callbacks.
- `PatchPriorityStore` and `PatchPreferences`: account-scoped preferences.
- `RunPlanner`, `WavePlanner`, `StockLedger`, `Supplies`: allocation and inventory planning.
- `StepEngine`, `WorldGuide`, `QuestTargets`, `WidgetOverlay`: interaction guidance.
- `queststate`: vendored Quest Helper farming-state data and observation logic.

Farming Patch Advisor (Seadeadmeat) provides the original farming data, timers, compost, contracts and storage infrastructure. Quest Helper (Zoinkwiz and contributors) provides farming-state logic and guidance inspiration. Original licenses and attribution headers are retained in LICENSE and LICENSE-QUEST-HELPER. No external Quest Helper dependency is required. Shortest Path is optional.

See CHANGELOG.md for development history. Automated tests do not establish in-game behavior; live client validation is required.
