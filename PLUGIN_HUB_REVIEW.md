# Farming Helper — review notes (v0.36-beta.1)

The guide observes client state and provides instructions; the player performs every game action. No automatic banking, planting, travel, world hopping or input injection.

## Integration review
- Bank Tags: native registered predicate, native layout, no user tag creation or explicit configuration edits. Native Bank Tags itself owns remembered-tab state and may save a layout if unexpected items are admitted; the predicate is restricted to IDs in the supplied layout. Verify persistence and cleanup with remember-tab enabled, manual navigation, bank close, logout, plugin disable, and both farming and birdhouse phases. No direct deletion of legacy layouts.
- Seed Vault: native client-owned item indices, IDs, quantities and actions remain untouched. Only verified visual positions, visibility, headers, category buttons and scroll bounds change. Restore before native rebuild and on exit. The generic UPDATE_SCROLLBAR script is presentation only and is invoked outside native script callbacks. The local scrollbar VarClientInt is visual UI state, not gameplay state. Custom views are optional.
- Shortest Path: optional PluginMessage integration, not game input. Start does not unconditionally clear another route. Existing ownership detection is best effort and legacy versions do not have modern ownership responses.
- Generated gameval constants are used where already verified; Seed Vault interface and native script identifiers still need an authoritative name mapping and reviewer verification. No guessed replacements.
- See LICENSE and upstream LICENSE-* files; upstream derivation and AI-assisted development should be disclosed in the eventual submission. This is not Plugin Hub approval.

## Manual acceptance checks
1. Toggle bank and vault views independently during farming and birdhouse runs.
2. Withdraw/deposit a portion of a stack, then its last item; check category, scroll, item actions and ranking fallback.
3. Change native categories and search; close/reopen vault and disable plugin while a view is active.
4. Open farming bank, withdraw/deposit owned items, switch native tags, close/reopen with remember-tab enabled; inspect for any unintended persistent layout/tag. Existing user tags must stay intact.
5. Check route replacement, user cancellation, hop and logout. No gameplay automation.
