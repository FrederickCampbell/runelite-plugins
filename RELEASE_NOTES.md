## v0.39-beta.2 — Compilation correction

- Capture the shared location context before its first use in the guide update; use normalized coordinates consistently in local watering and disease checks. No new features.

## v0.39-beta.1 — Consolidation and travel integration

- Cache Seed Vault patch eligibility on ordinary game updates, never invoke quest scripts inside native Seed Vault build callbacks, and invalidate the view when access changes.
- Share physical stock readers; canonicalize worn items for bank presentation only. Preserve exact crop stock and charged jewellery shelf positions. Release temporary registration and close an orphaned remembered farming tag without touching other tags.
- Share Quest Helper-derived coordinate conversion; recognize known POH templates and delegate travel to Shortest Path, with manual guidance when unavailable. Keep verified cave, underwater and sailing transitions.
- Accept additional valid native transport metadata. For confirmed ordinary-bank planting shortages, query Shortest Path's item-aware destination API and fall back to the existing advisory bank if unsupported. Check current target before clearing a modern route; legacy compatibility retained.
- Preserve all settings, presets and manual gameplay. Added targeted regression tests. Requires full staging build, live client checks and Plugin Hub packaging validation.

## v0.38-beta.1 — Seed Vault refresh lifecycle

- Reapply the verified native-widget projection at outermost Seed Vault script completion instead of waiting for the next ClientTick. Preserve the selected custom view and scroll, retain category controls across ordinary rebuilds, and retry after client scripts if native code overwrites the projection.
- Handle quantity-only invalidations at PostClientTick; keep the generic scrollbar script outside script callbacks. Failed geometry validation stays dirty. Check native widget identity, item ID and scroll height before reusing a projection.
- Debug-level lifecycle logging records native pre/post events and projection status. No changes to native item actions, listener ownership, quantities, or farming bank curation. Live validation required.

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

# Farming Helper v0.22-beta.1

Run-aware Seed Vault: exact remaining guide items, native-style alternating rows and a separator. Native browsing remains accessible. Live client validation required.

# Farming Helper v0.21-beta.1

Lifecycle hotfix on v0.20: hiding the sidebar or switching plugins keeps the active guide and route alive. The guide’s × ends the run. All existing farming, banking, and Seed Vault features are unchanged. Live client validation remains required.

v0.21: Compost Run and optional compost in Crop Run / Manual Run. Native bin-state decoding and supply estimates. Kastori flower completion requires local object verification; remote snapshots cannot suppress a visit. Unknown state and ambiguous geometry remain inspection steps. No automated gameplay actions.
