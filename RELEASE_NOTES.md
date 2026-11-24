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
