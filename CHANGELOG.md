# Changelog

## v1.06.1 — stabilization branch compliance hotfix

- Remove global desktop focus inspection and explicit focus requests from sidebar code and tests. Keep scroll anchoring and native Swing keyboard traversal; retain the existing search field during filtering and access refreshes.
- Replace the desktop-focus regression with isolated component-action, ranking and search-selection assertions. No display-focus opt-in is required for this test.
- Add a source-level input API guard to the Gradle test task and enable CI for the stabilization branch. No gameplay actions, crop guidance, storage views or navigation behavior changed.

## v1.06

- Fix live-state recognition for underwater seaweed despite plane-zero map markers; preserve underwater region/plane checks and resolve tool leprechauns in the player's current world view. Restores shared contextual compost and withdrawal guidance.

- Seed-box reliability: confirm closed-box transfers from player actions and loose-inventory/storage deltas, reconcile native contents and explicit game messages, track verified planting consumption, and keep character-local historical quantities without treating stale counts as current stock. Existing cyan inventory guidance remains shared.

- Add the default-on **Prefer contracts** checkbox under Crop priorities. Reserve one available active contract crop for an eligible selected Guild patch before manual and automatic preferences; fall back normally when unavailable. Keep existing living-contract protection independent.

- Require explicit display-test opt-in for the OS-dependent Swing keyboard focus regression; CI continues to run it on an isolated virtual display. Normal installer builds retain all other tests.

- Track the native seed-box container in slot order during the current session. Carried contents count toward crop allocation and supply readiness without inflating loose inventory or bank totals. Unknown contents require checking the box.
- Highlight the seed box for planting when the selected crop is stored inside; recommend direct planting only when its first compatible seed matches the plan. Otherwise recommend manual withdrawal. Birdhouse bait and tree preparation remain manual.

## v1.05

- Restore compact Seed Vault filtering; hide irrelevant placeholders and empty categories without changing native item identities or actions.
- Guide watering of eligible live, unwatered crops; recognize filled watering cans and Gricoller's can in leprechaun storage. Skip watering independently of the patch.
- Prefer carried supplies, then leprechaun storage, then bank stock. Hide covered compost and watering-can requirements; highlight the leprechaun and patch together when local supplies are needed.
- Add character defaults and temporary run overrides for watering and leprechaun assistance. Pin Start Run and Continue Run at the top of preparation.
- Large green leprechaun arrow and green outline, cyan Compost label with a red cross for pending optional compost on the active patch, and native exchange-slot guidance with a bounded withdrawal quantity for the actual stored can or compost.
- Contextual cyan action labels; green leprechaun arrow, outline and single exchange slot; red crosses for optional care. Immediate withdrawals use one item and native storage counts. Optional watering no longer gates planting completion and automatically expires after a short local offer.
- On the seaweed bank island, actual birdhouse inventory gates the rowboat; a missing kit highlights the bank chest with a green arrow, and generic routing resumes only after leaving the island. Correct underwater leprechaun detection and tolerate native camp dialogue formatting.
- Public author name: Freddy. Upstream credits and license notices remain intact.

## v1.04

- Guide the seaweed-to-birdhouse transition via the anchor rope, nearby bank chest when needed, and rowboat dialogue. Prefer sufficient carried birdhouse supplies before bank alternatives.

## v1.03

- Preserve fixed nearby patch pairings in mixed runs and guide local ultracompost withdrawals from tool leprechauns.

## v1.02

- Recommended items and Recommended teleports appear above preparation, resupply and patch steps. Each header has its own inline help button. Both lists collapse when the route starts or resumes. Start Run and Continue Run stay within reach while you scroll.
- The farming bank layout and compact Seed Vault keep native placeholders through withdrawals and deposits. A placeholder contributes zero to stock, crop choices and teleport recommendations.
- Supply rows show carried quantities and positive amounts last seen in storage. The missing-items count covers required supplies. Show farming bank explains when it needs an open bank or the Bank Tags plugin.
- The sidebar preserves position and keyboard focus as you edit. Manual and grouped selections update in place, and Individual Run search reuses its buttons. Long labels wrap at the available width.
- Character preferences and temporary run overrides are labeled. Locked patches show their reason and remain editable. Skip and stock reset dialogs name the action and default to Cancel.

## v1.01

- Custom Seed Vault views reapply their compact layout after deposits, withdrawals and depleted stacks. Native item slots and actions stay attached to the original widgets.
- Preparation updates its destination when newly observed storage changes the best supply source. Gathering the supplies releases the helper’s preparation route.
- Seeds and seedlings count as materials to prepare. The guide shows how many plantable saplings you actually carry.
- Species controls refresh when their crop-type default changes. Teleport recommendations distinguish nearby plots with different destinations. Newly actionable patches can join the remaining itinerary.
- The guide shares physical patch identities, crop definitions, stock observations and live scene objects across planning, highlighting and storage views.

The [release audit](docs/V1.01-AUDIT.md) records the subsystem counts and remaining live checks.

## v1.0

First stable release of grouped, individual and manual runs, stock-aware preparation, crop preferences, optional teleports and contextual help.

## Earlier milestones

| Versions | Changes |
| --- | --- |
| v0.42–0.50 | Species and patch preservation, replacement precedence, optional travel items, physical planting requirements, storage freshness and a smaller guide sidebar. |
| v0.39–0.41 | Player-controlled preparation, clustered routes, carried replacement checks, transport instructions and final sidebar artwork. |
| v0.33–0.38 | Contextual bank shelves, teleport eligibility, profile and interface cleanup, native layout ownership and Seed Vault rebuild handling. |
| v0.27–0.32 | Birdhouse stages, priority disease care, Fossil Island travel, optional Bank Tags shelves and sidebar navigation. |
| v0.20–0.26 | Grouped and individual presets, compost upgrades, stricter Kastori flower verification, run-aware vault filtering and manual templates. |
| v0.13–0.19 | Standalone patch guidance, live completion evidence, crop and supply ranking, scene highlights and compact Seed Vault views. |
| v0.1–0.12 | Quest Helper farming-state integration, Farming Patch Advisor catalogs, optional Shortest Path routing, per-patch settings and standalone run execution. |

The full beta changelog and imported Farming Patch Advisor history are preserved in the [v1.0 archive](https://github.com/FrederickCampbell/runelite-plugins/blob/2a86531/CHANGELOG.md).
