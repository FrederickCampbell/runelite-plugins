# Changelog

## v1.05

- Restore compact Seed Vault filtering; hide irrelevant placeholders and empty categories without changing native item identities or actions.
- Guide watering of eligible live, unwatered crops; recognize filled watering cans and Gricoller's can in leprechaun storage. Skip watering independently of the patch.
- Prefer carried supplies, then leprechaun storage, then bank stock. Hide covered compost and watering-can requirements; highlight the leprechaun and patch together when local supplies are needed.
- Add character defaults and temporary run overrides for watering and leprechaun assistance. Pin Start Run and Continue Run at the top of preparation.
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
