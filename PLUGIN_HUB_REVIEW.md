# Farming Helper — review notes (v0.37-beta.2)

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

## v0.37 bank ownership
The live shelf uses bank, inventory and equipment stock. Native layout cells for owned items outside the bank are visual placeholders only; they do not count as bank stock, trigger withdrawals or write a permanent tag. Check fully withdrawn stacks, worn-only gear, item deposits and switching farming/birdhouse stages in the live client.

## v0.38 Seed Vault lifecycle
Visual widget updates now run at outermost native script completion, following the native Anti Drag SEED_VAULT_BUILD post-hook pattern; the existing generic scrollbar script is called only outside the native script stack. Native item identities, actions and listeners are unchanged. PostClientTick checks for later native overwrites and retries. Live frame-level flicker and compatibility must be checked; no claim of verified in-game elimination.

## v0.39 integration and validation
No new external dependency or gameplay action. Cached eligibility is evaluated outside script callbacks. Native item identities/actions remain unchanged. Bank layouts retain actual bank/inventory/equipment ownership with canonical worn IDs used only for display; temporary tag registration remains in-memory, though native remembered-tab and layout persistence are still possible. Modern Shortest Path release checks the exact target before clear; the API does not provide exclusive ownership. Legacy clear remains best effort. House detection uses Shortest Path's published template bounds and landing coordinate; it does not scan/persist furniture or override Shortest Path settings. Confirm configured portals, alternate house themes, caves, sailing, fallback without Shortest Path, item-aware bank detours, manual continuation, charge changes, profile resets and vault rebuilds in a live client before submission. No assertion of build, in-game or reviewer approval is made by this source change.

## v0.39-beta.3 player control
The experimental item-aware bank-detour API and automatic mid-run banking diversions have been removed. Only initial preparation or an explicit Resupply request can direct a player to a bank; Entrana and birdhouse notices are advisory. Start/Continue remains a manual plugin button, not a game input action. This supersedes the bank-detour acceptance item above.

## v0.42-beta.3 polish
No new bank overlay or native widget actions. Existing vault outlines use an immutable missing-physical-quantity snapshot; inventory step outlines remain independent. Seedling aliases affect advisory material accounting only, never readiness to clear a living crop. No new dependency, game action, route behavior, or storage projection changes. Confirm withdrawals, growth transitions, and the 225px sidebar in the live client.
