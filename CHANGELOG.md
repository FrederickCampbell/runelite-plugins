## v0.37-beta.2 — Regression test setup

- Create and configure item-container mocks before stubbing client calls. Preserve both ownership assertions and all production Java from v0.37-beta.1.

## v0.37-beta.1 — Farming bank ownership

- Build the farming bank shelves from relevant items actually owned in the bank, inventory or equipment. Carried/worn items without a bank stack use native visual placeholders. Moving the same item between these locations does not change shelf membership or trigger a plugin layout rebuild.
- Preserve curated categories, run requirements, farming/birdhouse stage filters, temporary native Bank Tags registration and independent bank/Seed Vault switches. Seed Vault-only items and never-owned items are not added to the bank.
- Keep existing within-visit withdrawal placeholders. Stock allocation and manual gameplay remain unchanged. Added ownership and stable-layout regression tests; live native placeholder behavior requires testing.

## v0.36-beta.2 — Lifecycle regression expectation

- Correct the startup route assertion to require no unconditional Shortest Path clear. Keep the explicit end-of-run clear assertion and all other lifecycle checks. Production Java is unchanged from v0.36-beta.1.

## v0.36-beta.1 — UI reliability and compliance hardening

- Preserve selected Seed Vault view and local scroll across native refresh scripts; defer reapplication to the next client tick outside the script stack. Quantity-only changes with unchanged physical ID membership, widget identities, and ordered selection avoid custom relayout. Native rebuilds and changed selection still require reapplication.
- Keep native Bank Tags shelves and withdrawal positions. The registered predicate admits only physical IDs already present in the supplied layout, preventing plugin-originated unmatched additions; never explicitly save or delete Bank Tags configuration. No permanent tag tab is created. Native Bank Tags still manages its own remembered-tab configuration; live verification of implicit native persistence is required.
- Bank view and Seed Vault view remain independent character preferences. Explicit Show farming bank respects its toggle. Closing the bank unregisters the temporary predicate. Beginning a guide no longer unconditionally clears another helper's Shortest Path destination.
- The only Seed Vault script invoked by Farming Helper is UPDATE_SCROLLBAR for visual scrolling; no game actions, packet calls or withdrawal listener changes. Native geometry and script IDs not verified against generated names remain unchanged. Plugin Hub approval and live client validation remain outstanding.

## v0.35-beta.1 — Reliability & simplification

- Global character preferences are authoritative; crop care, tree removal and missing-planting-supply overrides are temporary per run. Old persisted run overrides are ignored, not deleted. Existing global, crop, patch, preset and storage preferences remain.
- Active guide shows current task first, automatically expands supplies during preparation, and retains completion/preservation history across farming and birdhouses. Run-wide ledger distinguishes completed, preserved, skipped, deferred and missing supplies; initially unscheduled patches are deferred rather than silently completed.
- Four settings categories; grouped presets under Patches, storage integration switches under Advanced. One missing-supply dropdown replaces two interacting controls. Combined Tree Run distinguished from individual Tree Run.
- Refresh patch access periodically for quest/diary/Hunter/Sailing changes and on start; update visible patch selection when eligibility changes. Same-profile world hops retain the active run and reissue navigation; logout or profile change resets it.
- Use one required-items map and shared physical-stock validation. Optional pot/seedling and diving gear appear for relevant run types; actual required supplies remain visible. Native bank layout, placeholders and final birdhouse stage unchanged.
- Added regression cases for run override lifetime, same-profile hop, stage-wide progress, and context-sensitive bank supplies. Existing live state readers and route selection remain intact.

## v0.34-beta.1 — One curated farming bank

- Keep one automatic native Bank Tags view; no favorites, hidden items, additional tabs or customization settings.
- Include owned empty/filled plant pots and seedlings (including watered seedlings) in a separate preparation shelf, alongside the existing compost supplies and selected planting items.
- Group empty buckets with compost rather than farming tools. Remove hammer and chisel from general equipment; explicit birdhouse requirements still include them in the birdhouse stage.
- Preserve only previously observed physical bank IDs as temporary withdrawal placeholders. Unowned items remain absent and placeholders never become stock.

## v0.33-beta.1 — Structured farming bank and expanded travel catalogue

- Native eight-column positional layout: Graceful/Farmer mannequins, cape/diving/diary blocks, jewellery families sorted by charge, rune matrix alongside staves and tomes, tablets, tools, compost, protection payments and crop-specific seed groups.
- Preserve native visual withdrawal placeholders during each bank visit; physical stock remains exact and separate. New deposits append without moving existing slots; reopening or birdhouse stage changes rebuild the shelf. No persistent bank changes.
- Expanded selected transport data from Shortest Path to include diary equipment, Max/Hunter/Fishing/Quest capes, ring of the elements, regional alternatives and additional useful tablets/scrolls. Exact tier/charge/unlock requirements remain authoritative; unknown gates fail closed.
- Existing farming logic and stage-specific supply selection retained. Added positional and withdrawal regression tests.

## v0.32.1-beta.1 — Fossil Island final leg

- Manual and Specialty runs keep all selected Fossil Island work after mainland/other locations. Hardwoods precede seaweed; birdhouses are last in their own resupply stage when mixed with farming.
- Selected birdhouses appear as pending guide rows without prematurely adding birdhouse supplies. Readiness is evaluated in the birdhouse phase. No new unselected patches are added.
- Freeze the island-last route against native route reordering. Nearby verified disease interrupts still have priority.

## v0.32-beta.1 — Visual identity and polish

- Native asynchronous OSRS item sprites on grouped/individual run selection and crop priority rows; no new runtime dependencies.
- Green-tinted preset cards, flat icon-led settings navigation, clearer checkbox contrast, shorter compost label with explanatory tooltip.
- No Resume Run or Home navigation during an active guide. Settings Back returns directly to the live guide; explicit End remains the run exit.
- All existing farming logic, settings and fuzzy bank behavior retained. No changes to game actions or route computation.

## v0.31.1-beta.1 — Lifecycle fix and fuzzy bank equipment

- New guide session IDs open the sidebar guide even for direct API starts; same-session ticks preserve the current screen. Restores the explicit End control tested by GuideLifecycleTest.
- RuneLite ItemVariationMapping recognizes recolored equipment and known charged teleport families. Exact usable transport rows take precedence; positive-charge aliases only inherit same-name transport family rows. Uncharged aliases and placeholders are not admitted through fuzzy matching.
- Seeds and other exact run requirements remain unchanged. Existing Shortest Path modes and item blocks remain authoritative.

## v0.31-beta.1 — Sidebar UX overhaul

- Scoped Verdana/native fallback theme, native Swing/FlatLaf controls, consistent vector icons, fixed navigation header.
- Persistent screen state; live guide updates no longer replace open settings. Back returns home without ending, explicit End and Resume retained.
- Searchable individual runs, sortable manual and permanent patch browsers, grouped preset accordions, independent crop-priority settings and storage/advanced menus.
- Collapsible supplies/completed patches, visible listed progress, context-specific run settings and clear skip wording. Existing configuration keys and farming/banking backend preserved.
- Progress reflects the visible guide rather than inventing a total run statistic; live-client verification required.

## v0.30.1-beta.1 — Slot-ordered bank shelf

- Native transient Bank Tags Layout, sorted by equipment slot then separate rows for permanent teleports, charged jewellery, rune/tablet supplies, tools, compost/payments and seeds. Empty categories are omitted; long categories wrap to the next row.
- Layout contains only physical bank stock; no virtual missing-item slots or duplicate items. Manual bank browsing and existing skip/guide behavior retained.
- No changes to actual bank order. Remove the reserved tag's temporary layout at run end.

## v0.30-beta.1 — Curated farming bank shelf

- Shared owned farming gear and teleport candidates across run types, selected run supplies, narrower birdhouse resupply view. No optional recommendation panel or Farming Helper bank highlights.
- Native Bank Tags filtering retained; Inventory Setups uses the same underlying API.
- Item teleport rows adapted from Shortest Path; exact owned IDs, mode, explicit blocks, known skill/quest/var conditions checked. Destination-scoped blocks conservatively exclude the family; unknown gates omit candidates.
- Coral equipment required; ordinary seaweed gear optional. No route changes or automatic withdrawals.

## v0.29-beta.1 — Active Run bank and birdhouse navigation

- Native transient Bank Tags predicate for current guide requirements; auto opens once per bank visit and allows ordinary browsing or manual reopening. No permanent item tags or automated withdrawal.
- Tar Swamp, Mushroom Meadow, southwest valley, northeast valley. Distinct site coordinates and live object approaches.
- Use Shortest Path query transport metadata to outline nearby rowboats and mushtrees and show their next interaction. Legacy versions retain native route-only fallback.

## v0.28.1-beta.1 — Universal disease priority interrupt

- Verify live disease on an identified local patch before ordinary planning, even in resupply and birdhouse phases.
- Suspend the normal cursor, crop, planting progress, route order, and banking state rather than inserting a replacement stop.
- Highlight only the cure item and affected patch, then resume on recovery or manual skip. If the target leaves local view, resume without claiming it was cured.
- Skip is separate from ordinary patch skipping; dismissed interruptions are remembered for this run.

## v0.28-beta.1 — Specialty birdhouse finish

- Specialty includes optional birdhouses by default; farms and seaweed first, then a separate inventory phase at Bank Chest-wreck.
- Keep Fossil Island last and preserve fixed stage order while retaining Shortest Path navigation per leg.
- Birdhouse requirements appear only after farm work; use carried and bank stock, not inaccessible Seed Vault stock.
- Prefer potato seeds and cheap alternatives, choose highest usable stocked house/log tier, and recover clockworks from mature houses.
- Remove the redundant Fossil Island manual template; standalone Birdhouse Run remains.

## v0.27-beta.1 — Crop care and flower progression

- Optional watering for local dry allotment, flower and hops crops when a usable can is carried.
- Detect verified nearby diseased patch objects outside the selected run, add a cure-only detour, and resume without changing planting allocation.
- Include flowers by default in Crop Run; reserve stocked white lily seeds first and preserve living lilies. Existing explicit preset exclusions remain respected.
- Use secateurs for diseased bushes as well as trees and coral; plant cure for ordinary crops.

## v0.26.1-beta.1 — Exclude placeholder stock

- Reject bank and vault placeholder item definitions before counting physical stock, even if the quantity is positive and the name matches a seed.
- Sanitize and persist existing cached snapshots on load; preserve genuine items.
- Exclude placeholders from custom vault ranking and raw-seed name resolution.
- Add regression coverage for same-name calquat placeholders and cached phantom stock.

## v0.26-beta.1 — Stock evidence and saved clearing preference

- Clear Patches defaults on for a new character preference, remembers explicit opt-out across runs and sessions, and is not reset when Hide Seedless Patches is toggled.
- Raw seed preparation shows physical carried/bank/vault quantities and which storage has been observed this session; planned saplings are marked with their preparation count.
- Refresh open storage before seedless preference changes; warn about unverified storage and allow explicit, non-destructive reset of cached observations.
- Regression tests for absent calquat stock, one raw seed allocation, and cleared observations.

## v0.25-beta.1 — Focused inventory guidance and seedless clearing

- Separate current-action inventory outlines from whole-run shopping lists. Only the first inventory slot containing each requested item is highlighted.
- Compost before planting is an explicit step with native per-patch tracker confirmation and manual override.
- Optional Clear Patches under Hide Seedless Patches retains occupied actionable seedless stops for XP and clearing, with no replacement or protection supplies.

## v0.24-beta.1 — Health-check state and completion audit

- Normalize all 31 documented Check-health states across fruit trees, trees, hardwoods, bushes and specialty crops before route planning and cached prediction.
- Require live empty-to-growing transition for replacement completion. Already-growing observations are not recorded as completed work.
- Reject ambiguous typed physical patch identities; retain Kastori flower object verification.
- Gnome Stronghold fruit tree before regular tree within its route group.
- Regression coverage for raw values, patch isolation and complete replanting sequence.
- CI workflow covers version branches.

## v0.23-beta.1 — Run preferences, dedicated vault view and templates

- Character-local policies for individual/grouped/manual runs; optional protection, compost and removal.
- Raw tree seeds contribute to allocation, with separate sapling preparation supplies.
- Active Run vault category and manual preset templates, including Fossil Island trees + seaweed + birdhouses.
- Four birdhouse stops, native tracker readiness, material and bait estimates, and local object guidance. Remote routing uses mushtree approach points until the exact object loads.

## v0.22.1-beta.1 — Run stock toggle and sidebar layout

- Optional per-run Hide Seedless Patches reserves actual observed planting stock, preserving non-replanting work and restoring hidden stops when disabled.
- Sidebar tracks viewport width; satisfied supply counts use a green icon with exact counts in tooltip.

## v0.22-beta.1 — Run-aware Seed Vault

- Use exact remaining guide requirements in custom vault views; open to Custom View during a run, native categories remain accessible.
- Alternate native row backgrounds and add a small separator; preserve withdrawal handlers and restore native widgets.

## v0.21-beta.1 — Compost runs and Kastori flower state validation

- Eight native compost bins; optional Crop Run component and Manual Run selection. Live contents determine fill, close, upgrade, and collection steps.
- Confirm nearby Kastori flower object and transmit identity; ignore unverified remote flower completion.

## v0.20.1-beta.1 — Keep runs active when the sidebar is hidden

- Sidebar deactivation no longer stops an active farming run.
- The guide header × is explicitly labelled End farming run; normal session cleanup remains unchanged.
- Regression test covers panel hide/reopen, progress while hidden, and explicit end.

## v0.20.0-beta.1 — Grouped and individual presets

- Four configurable grouped presets plus Manual Run, all individual crop-type shortcuts, and two collapsible home sections.
- Guild-first reference routes, optional native refinement, existing eligibility and crop ranking.
- Advisory preparation/resupply, required-slot estimate, Entrana equipment warning and Vinery supplies.
- Default flowers and grapes off in grouped presets; spirit trees remain optional.

## 0.19.0-beta.1

- Bank Tags-inspired native Seed Vault repositioning: compact and rank verified icon/label pairs without remapping native indices or withdrawal actions.
- Collapse verified empty sections and update scroll height; preserve native layout for ambiguous geometry.
- Restore original positions, visibility and scroll on native rebuild or exit. Preserve contract/favourite controls.
- Added pure layout and native widget lifecycle regression tests. Live withdrawal and responsive layout validation remain required.

## 0.18.0-beta.1

- Shared healthy-cycle XP-per-seed crop scoring with documented yield estimates and unknown-yield lower bounds.
- Native Seed Vault Highest XP, Top 3, Custom View prototype; preserve native item/menu identity and restore visibility.
- Preserve living white lilies and matching Farming Guild contract crops.
- Correct jute seed quantity.

## 0.17.0-beta.1

- Manual Run replaces Combined Run: crop-level checkboxes, collapsed per-patch choices, select/clear all, per-character remembered selections.
- Group initially actionable patches into farming sites; preserve familiar shortcut routes.
- Bounded Held-Karp/nearest-neighbour/2-opt/or-opt spatial ordering; optional native Shortest Path multi-target refinement before leaving the first group. Pin remaining route; do not re-add growing patches mid-run.
- No travel atlas, bank tabs, saved sessions, loadouts or new configuration pages.

# v0.16.0-beta.1 — crop intelligence, navigation and presentation

- Stock-aware individual and category priorities with automatic fallback; persistent character-local storage observations.
- Optional Shortest Path reachability queries, stale-route refresh and ownership checks; explicit cave/underwater entry/exit and Sailing mooring stages.
- Keep Quest Helper exact coordinates; reconcile Etceteria with Shortest Path, use live varbit-matched object identities and collision-aware perimeter approaches.
- Correct mature coral and permanent-tree handling; cure supplies and stable active seed allocation.
- Native item sprites and compact guide sections; update quantity/instruction rows in place.
- Add regression tests; live specialty routes remain subject to in-client testing.

# v0.15.0-beta.1 — Quest Helper presentation and interaction pass

- Separate live required/recommended counts and stock hints; eligible missing crop requirements remain visible.
- Editable priorities, individual overrides and minimal compost/gardener preferences.
- Session-local completion, close lifecycle, and conservative object/seed highlights.
- Separate surface approaches for Hespori, Seaweed and Coral; remove independent Falador teleport hint.
- Add regression scenarios; live client validation remains necessary.

# 0.14.0-beta.1

Keep character-specific crop priorities and individual patch toggles. The guide advances independently of Quest Helper and Shortest Path. Expand the items and patches sections as needed. Closing the guide ends the run.

# 0.13.0-beta.1

Simplified the plugin to run selection, guide and preferences. Restored Quest Helper herb destinations and ordering, delegated travel to Shortest Path, and separated seed allocation from patch eligibility.

# Farming Helper development history

## v0.12.0-beta.1 — Patch coverage and lifecycle reliability

- Published RuneLite farm-area markers extend navigation to all catalogued categories, distinct from exact Quest Helper object tiles.
- State registry supplies canonical physical patch identities; reject incomplete or duplicate grouped identities. Regression tests assert 99 distinct non-compost identities and routable areas.
- Normalize cactus health checks and depleted perennial crops; preserve established redwoods, spirit trees, bushes and cacti. Respect known spirit-tree planting limits.
- Prioritize Quest Helper/Time Tracking observations; legacy exact timer fallback only. Remove obsolete compost confirmation checks from live execution.
- v0.11 CI passed; v0.12 requires a fresh build and in-client validation, particularly specialty/underwater interaction and transport.


## v0.11.0-beta.1 — Quest Helper-style execution

- Guide starts immediately and routes without supply confirmation. Plan all eligible patches in one itinerary, even when stock is missing or estimated supplies exceed 28 slots. Banking is discretionary.
- Retain stock-based priority allocation and explicit overrides; suggest eligible missing stock instead of dropping patches. Show live required and recommended counts and observed storage source hints.
- Use Quest Helper's auto-weed and Barbarian planting varbits for tool recommendations. Fix cured crop transitions and remove mandatory compost progression; absent gardener payments do not block progression. Add regression coverage.
- Build and live-client validation required before release.

## v0.10.0-beta.2 — Smart readiness and simplified controls

- Automatically omit growing, disabled and known inaccessible or level-ineligible patches before allocating supplies; recheck current patch eligibility during execution. Unknown crop state is not treated as growing.
- Display `Wait for plants to grow.` if all eligible observed patches are growing; missing stock and unknown eligibility remain distinct.
- Replace bottom Back/Next/Pause/End controls with Skip/End. Skip persists independently from completed patch IDs. End clears the guide and path. Bank replan remains in the supply section.
- Always advance on observed completion; Shortest Path remains enabled by default with no hard dependency. Add readiness regression coverage. Build and gameplay validation pending.

## v0.10.0-beta.1 — Run execution and inventory-only planning

- Remove the global stop limit from the active planner and settings; slot budgeting remains enforced.
- Start directly in the active guide with a live, collapsible checklist; automatically enter guidance when ready, or explicitly continue with missing items.
- Show completed and upcoming stops; update remaining travel item requirements after each completed patch.
- Use Quest Helper verified navigation targets and explicit player pins, independent of legacy timer coordinates; retain timer/state and resource infrastructure.
- Preserve current preferences; add regression cases for long stackable and nonstackable loadouts. Live RuneLite verification is still required.

## v0.9.0-beta.1 — UI and architecture refactor

- Rename the Java source/test packages to `com.farminghelper`, plugin entry point to `FarmingHelperPlugin`, and config group to `farming-helper`. Pre-Hub configuration reset intentionally requires new settings; old group remains untouched.

- Component-local FlatLaf styles where supported, compact fallback controls, consistent typography and dark narrow scrollbars.
- Reusable headers, rows, search and normalized item sprites; simplified home, collapsed filters and advanced patch controls.
- Extract crop priority editor and common layout helpers from the main sidebar; avoid periodic rebuild when observations are unchanged; normalize sprites only after the item image has loaded.
- Single physical crop priority store; obsolete per-run UI removed. Crop priorities reset for this pre-Hub beta.
- Remove legacy panel from plugin lifecycle, add opt-in bounded diagnostics and a headless Swing component preview.
- Existing planning and guidance engines retained. Live client testing required.


## v0.8.1-beta.1 — Unified Patch Settings

- Direct run buttons with combined-run membership presets; one Global Settings page and one Patch Settings page.
- Separate physical patch types, progressive ranked dropdowns, no duplicate preferences, automatic saves and collapsed locations. Retains reserves and route editing.
- All run selections share physical patch preferences, including combined runs. Older category settings are filtered into their constituent types; previous merged-only overrides remain stored but do not supersede shared settings.
- Display planting unlocks independently from access gates; regression tests cover Anima 65/76, independent type choices and stock fallback.



## v0.8.0-beta.1 — Polish Update

- Local forest-green Swing theme: buttons, checkboxes, dropdowns, fields, spinners and lists; no global LookAndFeel changes.
- Collapsed run cards and OSRS item sprites replace the run dropdown and separate editor. Centralized cog access and inline crop priorities. Hide unavailable Resume actions.
- Settings groups and patch categories/locations start collapsed. Routine settings and crop priorities save automatically; explicit route save retained.
- Global/category/location/individual patch preferences, enabled counts, unlock distinction, full-scope bulk actions.
- Remove legacy overlay registrations and fallback recommendations. Preparation/restocking alone exposes bank/vault outlines/filter; active run exposes Quest Helper-style action hints. Idle, paused and finished states show no farming overlays.
- Supply checklist at start, default stop cap 28 with saved limits preserved. Added overlay policy and local theme regression tests. Live RuneLite testing still required.


## v0.7.0-beta.1 — Quest Helper interaction parity pass

- Public name changed to Farming Helper; consolidated release on the default branch. All former development commits are retained. Internal configuration and account-specific preferences are unchanged.

- Imported the original 32 TreeRun/HerbRun patch object IDs and 22 gardener IDs from Quest Helper, preserving its attribution and using the existing DirectorWaypoints coordinates. Event-tracked exact matching precedes the conservative Advisor fallback.
- Separate tree removal (pay or chop), stump clearing, raking, planting, compost and protection actions. Budget 200 coins per potentially paid removal; manual chopping still needs an axe.
- Profile-scoped compost selector supports none, regular, super, ultra or filled bottomless (ultra fallback), preserving legacy boolean settings. Both protection and compost may be enabled independently.
- Active interaction snapshot drives route item/spellbook highlights, patch/NPC world hints, specific gardener dialogue choice outlines, active inventory highlights and sidebar. Generic confirmations are left manual to avoid false matches.
- Current patch card shows observed action transitions; advanced details are tucked away. Existing planner, storage scanner and Shortest Path integration remain.
- Added regression tests for exact targets, action selection, removal coins, dialogue matching, travel hints and compost migration/stock. In-client testing remains required, particularly ambiguous specialist patches, garden dialogues and care confirmation.


## v0.6.1-beta.1 — In-world guidance and simple workflow

- Reused Advisor's patch-object tracking and footprint overlay; adapted Quest Helper's active ObjectStep clickbox, down-arrow and item-icon presentation to the Director's actual planned step.
- Highlight the active patch, or its registry-defined gardener during protection, rather than all nearby recommendations. Suppress ambiguous targets instead of guessing; configurable highlight color and visibility.
- Simplified the default guide to current action, destination, progress, next stops and navigation. Advanced controls and planning details remain available on demand.
- Live supply checklist with carried quantities; wait at preparation until Begin farming, then request Shortest Path and enable progress tracking. Replan after banking.
- Group default mixed itineraries by location; preserve explicitly saved merged route order.
- Newly planted crops awaiting configured compost remain on the compost step until care confirmation or manual Next. Retain previous state readers and planner.
- Tests cover target matching (exact, unique and directional pairs), ambiguous numbered patches, stable location grouping and compost action transitions. Live RuneLite visual and care-tracking behavior still requires player testing.


## v0.6.0-beta.1 — UI polish and final integration pass (2026-11-23)

- Reworked sidebar spacing and scroll behavior for RuneLite's native panel width; highlighted current/completed stops and added progress bar, active destination, compact navigation and a separate readable loadout page.
- Added visual crop ranking and stock reserve controls, retaining the previous profile encoding and the existing Planner/CropCatalog.
- Added a home-screen merged preset picker, deletion and cleanup, per-preset/category reservations, omitted-route-stop restoration, and patch counts/attention filter.
- Connected existing Advisor bank filter, bank and gameplay checklist overlays and item outlines to the active remaining Director wave. Kept the legacy fallback and independent contract checklist.
- Calculated configured travel slot costs using actual consumable item stackability; added player-pinned per-patch waypoint navigation, explicitly separate from crop state.
- Improved payment handling: available and feasible tree payments are allocated, infeasible/missing payments are reported while allowing the preferred crop; harvest-only checked trees are retained.
- Exposed per-character compost preference and harvest space reserve; filtered known locked compost destinations; improved profile-safe status refresh and new-run confirmation.
- Added regression coverage for priority controls, reserve serialization, travel costs, player waypoints, narrow viewport policy, protection feasibility and active-bank checklist projection.
- **Beta:** automated builds are not a substitute for RuneLite/Windows playtests. Exact specialist routes, remaining transport eligibility/charge rules, actual harvest capacity, live overlays and GUI behavior still require verification.


## v0.5.0-beta.1 — Functional integration and hardening (2026-11-23)

- Reused the existing planner and wave splitter; applied slot limits to harvest-only and protection-only stops and allowed planning to continue past an impossible first stop.
- Added per-wave rake/spade/dibber/secateurs and compost requirements, ordinary bucket slot costs, filled-bottomless-bucket option, source-aware withdrawals and missing-supply warnings. Axe alternatives, charges and harvest yields still need live verification.
- Replanned at banking checkpoints rather than continuing with obsolete waves; fixed manual destination jumps so earlier unfinished stops remain in the itinerary.
- Reconciled known unlock checks with upstream Quest Helper's HerbRun and TreeRun requirements, including Varlamore, Fossil Island, Harmony diary, Prifddinas, Sailing and Farming Guild tier access.
- Audited Advisor/Quest Helper patch identity coverage and added specialized health-check and anima-expiry normalization; protected permanent spirit trees from routine removal guidance.
- Extended the Compost Run into a sequential, manual guide with per-bin supply estimates and optional observed/nearby-area route requests.
- Added a profile-specific Shortest Path switch and reviewed the upstream path/clear message contract.
- Hardened logout/profile handling, asynchronous planning and storage observations; ignored closed-interface bank/vault events and preserved saved route membership when global exclusions change.
- Added regression tests for the above pure models and recorded an in-client acceptance checklist.
- Remaining: live RuneLite/Windows testing, precise specialist destinations, full teleport/charge and spirit-tree-limit modeling, visual/interaction refinement, complete legacy-control migration.


## v0.4.0-beta.1 — Unified sidebar and global Patch Manager (2026-11-23)

- Replaced the visible Guide/Advisor tab arrangement with one sidebar and five pages: Run Selection, Active Guide, Run Editor, Patch Manager and Global Settings; added a dedicated Compost Run observation page.
- Preserved the Advisor backend (crop catalogs, bank/vault stock, timers, contracts, protections, existing overlays) and optional Shortest Path navigation.
- Added a global per-character patch-enabled registry. Physical patch names are expanded from Quest Helper before exclusions apply, so an individual allotment can be excluded without disabling its neighbor.
- Implemented collapsible/searchable patch categories, individual switches and bulk location/category choices; displayed known unlock restrictions and independently observed readiness, including unverified states.
- Added saved, reusable Merged Runs, combining any Advisor farming categories through the existing Director planner and wave splitter.
- Added per-category priority inheritance with optional per-preset priority overrides. Regular crop selections remain automatic or manually ranked; current-run and permanent single-patch crop overrides now have distinct scope.
- Moved most planner controls out of the active guide. Added ready-only and include-unverified preferences, a shorter active-guide display with optional diagnostic details, and inline patch-edit actions.
- Moved route ordering and travel choices into another sidebar page; merged-run presets retain independent itinerary and teleport choices.
- Added an Advisor-backed farming status page exposing observed timers and farming contracts without restoring a second sidebar GUI.
- Preserved completed steps and active mode while opening editor/settings/patches; banking replans continue with the active run and respect newly disabled patches.
- Added regression tests for merged priorities, patch expansion/exclusions, saved profile isolation and active-run mode selection.
- Known limits: patch unlock verification is incomplete; the compost page shows existing observed actions and supplies but not a full step-routed itinerary; some older Advisor controls need equivalent unified UI; no in-game/Windows client acceptance testing has been performed.


## v0.3.0-beta.1 — Intelligent banking and loadout planning (2026-11-23)

- Built a multi-wave planner that preserves unplanned patches instead of dropping the remainder at 28 slots.
- Added bank checkpoints with user-selected or approximate nearest-listed bank waypoints; supports optional Shortest Path event routing and manual next-wave confirmation.
- Source-aware inventory/bank/Seed Vault ledger with estimated per-wave withdrawals and missing-item breakdown.
- Added inventory stop limits and dynamic per-wave teleport slot budgeting, excluding equipped travel items.
- Counted consumable teleport uses, warned on missing configured travel items, and retained the existing Advisor route preferences.
- Added safe auto-advance for harvest-only stops only after an observed live state transition to empty.
- Kept manual crop and route overrides, tree protection gating, resume/replan and Herblore-focused presets.
- Expanded regression tests for planning waves, bank eligibility, source-aware stock, travel supplies and progress safety.
- Java build and tests run on GitHub Actions; in-game RuneLite verification remains outstanding.



## v0.2.0-beta.1 — Consolidated farming director (2026-11-23)

- Generalized Guide to every patch type in Advisor's crop catalog, including multi-type combined runs.
- Individual Quest Helper patch decoding for herbs, trees, fruit trees, calquats, hardwoods, allotments, bushes, hops, flowers, seaweed, grapefields, cactus and specialist patches.
- Added exact Quest Helper fruit/calquat/hardwood coordinates, observed Advisor targets and clearly labelled non-routable region hints.
- Automatic state-driven actions and step progression with manual overrides and optional Shortest Path integration.
- Global 28-slot seed/sapling inventory planner, grouped-patch splitting, reserved seed stock, tree protection, and harvest-only stops.
- Individual crop overrides, Herblore-supplies preference preset, optional nearest-stop proximity heuristic and opt-in replan after banking.
- Saved per-character run checkpoints, resume after relog, and replan-remaining workflow.
- Streamlined guide into a compact sidebar with expandable planner options and preserved Advisor tab.
- Unit tests and GitHub Actions builds. Interactive in-client verification is still outstanding.



## v0.1 — Quest Helper farming-guidance integration (2026-11-23)

- Preserved Farming Patch Advisor's implementation as the main engine, with a separate plugin name and configuration.
- Ported Quest Helper's actual FarmingWorld region/patch mapping, varbit decoder, crop states and Time Tracking prediction logic under a standalone package with BSD attribution.
- Added live condition-based instructions for herb and tree runs: harvest, check health, clear, cure, dig, plant and gardener protection.
- Added automatic move-to-next after observed growing/protected states, click-to-jump destination list, Pause/Resume and manual fallback. Shortest Path receives the same message structure as Quest Helper.
- Added allotment runs, splitting Advisor's grouped allotments into Quest Helper's independently tracked patches. Allotment seeds count as stackable in the loadout.
- Improved quantity planning to skip known already-growing patches and retain existing trees requiring protection without consuming another sapling.
- Added tests for patch access and Quest Helper state decoding, step behavior, seed priorities, reserves and inventory limits.
- Gradle build and tests run in GitHub Actions; interactive RuneLite testing still required.

---

# Farming Patch Advisor upstream version history (imported without reinterpretation)

## V1.11 — 2026-09-26

- Fixed stuttering and short freezes when **All** is selected in the Farm Run
  side panel.
- The panel now preserves its existing cards and updates countdowns, growth
  stages, status colors, and compost timers in place once per second.
- Full card reconstruction now happens only when the underlying patch,
  contract, compost-bin, inventory, route, or plugin-setting data changes.
- Hidden side panels no longer perform periodic visual refresh work.

## V1.10 — 2026-09-24

- Added a **Farm run** selector inside **Route & Teleports** containing every
  enabled crop-run option currently available in the side-panel dropdown.
- Switching runs inside the pop-out now loads that run's independent patch
  order, membership, and teleport selections without closing the editor.
- Keeps edits for every visited run as drafts and saves them together only when
  **Save route** is selected; **Cancel** still discards all changes.
- Each selected patch continues to offer only travel methods that reasonably
  serve its location, allowing players to choose the method they own and use.
- Groups allotment and flower patches at the same farming location into one
  route stop displaying only the location name. The shared teleport choice is
  applied to every underlying allotment and flower patch at that stop.
- Added **Reset selected run**, which restores the active run's default order,
  membership, and `None / no item needed` teleport choices when the editor is saved.
- Added regression coverage for run-selector availability, combined tree runs,
  and lossless grouped-stop expansion.

## V1.9 — 2026-09-24

- Fixed the RuneLite client appearing frozen when **Compost Run** was opened by
  making the compost target config enum accessible to RuneLite's config proxy.
- Removed the duplicate fill quantity from compost action text. Filling bins now
  show the amount once in `Status: FILLING x/x`, followed by a clean action such
  as `Add Watermelon`.
- Confirmed in-game on Sir_deadmeat that standard and big-bin capacities, state
  transitions, timers, persistence, settings synchronization, scene highlights,
  movable checklist, bank-side checklist, and bank filter all work correctly.

## V1.8 — 2026-09-24

- Added a dedicated **Compost Run** to the Farm Run dropdown for Falador,
  Morytania, Catherby, Ardougne, Kourend, the Farming Guild, Civitas illa
  Fortis, and Prifddinas.
- Added character-specific compost-bin state tracking using RuneLite's current
  standard and big-bin farming varbits. Cards show contents, fill amount,
  processing countdown, readiness, and the next useful action.
- Added clean whole-bin scene outlines: gray when empty or unknown, orange while
  filling, white while processing, and green when ready or being collected.
- Added configurable compost targets, individual bin locations, ground
  highlights, and supply/bucket checklist controls under **Compost bins**.
- Added compost-run supplies to the movable checklist, bank-side checklist,
  bank filter, and item highlights. Compost potion bottles count their actual
  doses, and an owned bottomless compost bucket replaces ordinary buckets.
- Based the state ranges and regional transmit varbits on RuneLite's open-source
  Time Tracking farming implementation, with regression tests for standard and
  Farming Guild big compost-bin states.

## V1.7 — 2026-09-18

- Fixed the Civitas illa Fortis patch changing from `READY` to `DEAD` when
  harvesting began. A temporary `Clear` action during a confirmed harvest now
  remains a live harvest transition.
- Explicit dead and diseased states still take priority, and completed timers
  are removed only after the patch object becomes genuinely empty.
- Refreshes the pending harvest state on every harvest or pick action so
  multi-pick crops continue to be evaluated from their latest object state.

## V1.6 — 2026-09-18

- Audited every protection payment used by the plugin against the current Old
  School RuneScape Wiki, including allotments, hops, bushes, trees, fruit trees,
  hardwoods, special patches, cacti, seaweed, and coral. All 51 existing payment
  item IDs and quantities were correct.
- Updated payment labels to use exact in-game item names where the checklist had
  used informal plurals, such as `Coconut`, `Watermelon`, and `Ground tooth`.
- Added an exhaustive regression test for every payable crop in the catalog and
  for crops that correctly have no gardener protection payment. Grape-vine
  saltpetre remains a planting requirement, not a protection payment.

## V1.5 — 2026-09-18

- Added **New custom route**, **Add patches**, and **Remove selected** controls
  to **Route & Teleports**. Players can build a route from enabled patches in the
  selected run, then arrange them by dragging or with the move buttons.
- Limited each patch's teleport dropdown to travel choices that reasonably serve
  that patch's area. `None / no item needed` remains available everywhere.
- Invalid older teleport selections safely fall back to no required item, while
  disabled route stops retain their saved position and membership.

## V1.4 — 2026-09-18

- Fixed living, harvestable cactus and potato cactus patches being marked dead
  when their object actions include both a harvest option and `Clear`.
- Explicitly dead plants and non-harvestable plants whose only applicable patch
  action is `Clear` continue to use the red `DEAD` state.

## V1.3 — 2026-09-17

- Corrected all five hardwood growth timers to match RuneLite's 640-minute
  growth cycles: teak 4,480 minutes; mahogany, camphor, and ironwood 5,120;
  rosewood 5,760. Inspected stage estimates now use the same cycle length.
- Added checks for planted and inspected stage calculations for every hardwood.
- Existing saved hardwood timers using the old estimates are corrected when that
  character's timers load; unrelated timer values are left intact.
- These are maximum estimates until a character's growth-tick offset is known;
  RuneLite Time Tracking can predict a more precise completion time.

## V1.2 — 2026-09-17

- Sidebar patch and contract cards now grow to fit wrapped text instead of relying
  on fixed heights, including long names, payment details, timers, and teleports.
- Compact header buttons fit the RuneLite sidebar; the route editor adapts its
  scroll area to the available screen and wraps long stop names.
- Local visual polish pending in-game confirmation.

## V1.1 — 2026-09-17

- Added a Route & Teleports editor with drag ordering, up/down controls, default
  order, Save, and Cancel; preferences belong to each character and run selection.
- Custom routes follow enabled patches and locations, with matching panel and
  ground-label patch numbers. Disabled stops retain saved choices.
- Added teleport checklist sections, equipped-item detection, and bank filter/highlight
  support for chosen supplies. Reusable items are deduplicated and tablets count per visit.
- Local update; in-game verification and publication are pending.

## V1.0 — 2026-09-17

Established the current local plugin as the V1.0 baseline, including the existing
Morytania patch-matching fix, performance optimizations, and harvest-status changes.
These local changes still require in-game confirmation and publication.

Teleport checklist and custom route ordering are preview concepts and are not
included in V1.0. The next update will be V1.1, followed by V1.2, V1.3, and so on.
