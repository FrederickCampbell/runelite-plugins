# Farming Helper review and live-client checks

The player performs all game actions. The plugin reads farming observations, displays instructions, and changes the presentation of opt-in storage views. Shortest Path receives navigation messages. The v1.06.1 stabilization hotfix adds no runtime dependencies and removes desktop focus inspection and explicit focus requests. The Gradle test task checks production and test Java for prohibited input APIs. Native Swing controls retain normal keyboard behavior; no game input is generated. Development builds and original licenses are documented in [Development](docs/IMPLEMENTATION.md).

| Integration | Review surface |
| --- | --- |
| Seed Vault | Native item widgets retain their IDs, indices, quantities and listeners. The helper restores owned visual changes before native rebuilds and reapplies validated layout afterward. The generic scrollbar refresh runs outside native script callbacks. |
| Bank Tags | The active-run predicate admits relevant physical IDs and their native placeholders in the supplied temporary layout. The native Bank Tags plugin manages remembered tabs. |
| Shortest Path | The guide tracks its displayed leg. Modern ownership checks compare the reported destination before clearing. Legacy integrations without ownership responses are never cleared automatically. |

## Live acceptance

1. In Highest XP, Top 3, Custom and Active Run, deposit a new sapling, change a seed stack, withdraw the last item and redeposit it. Check compact headers, scroll, tooltip, right-click quantities and the inventory received. Switch native categories, search, resize, close and disable the plugin.
2. In preparation, open previously unobserved bank and vault stock. Check the destination changes and the route disappears when supplies are gathered. Start with missing items and manually resupply later. Replace the helper route with a personal Shortest Path route and check ownership.
3. With Bank Tags remembering the last tab, switch between native tags and the farming shelf. Withdraw, equip, close, reopen, switch to birdhouses and disable the plugin. Inspect saved tags and layouts.
4. Verify Kastori flowers next to fruit trees, numbered vine patches, both compost-bin sizes, protected contracts, a newly actionable remote patch and a disease interruption. Check same-character hopping and character switching on specialty travel legs.

5. In an allotment, flower or hops run, verify carried, leprechaun-stored and bank-only watering cans and compost; check the large green arrow, green leprechaun outline, cyan contextual action labels and red optional-care crosses, exchange slot and recommended quantity (including ordinary cans, Gricoller's can, full inventory, and partial storage), optional watering skips, and temporary settings reset. Verify Start/Continue stays pinned above the scrollable guide.
6. In a mixed seaweed/birdhouse run, verify underwater exit, mandatory island-bank guidance for missing birdhouse supplies (green chest outline and arrow; no rowboat path), rowboat dialogue highlight and sufficient-carried-supply behavior.

Automated build and regression tests are tracked in GitHub Actions. Live acceptance remains a player-side check.

7. Check an open and closed carried seed box, transfer seeds between it and inventory, plant directly, reorder compatible species, deposit the box, hop and change character. Verify supply counts and highlights, including birdhouse bait and raw tree seeds. Unknown contents must not satisfy requirements.

[Plugin Hub development and review](https://github.com/runelite/plugin-hub) · [RuneLite feature guidance](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) · [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)

8. On the stabilization build, manually tab through the sidebar, reorder and remove crop priorities, use help and confirmation popups, filter patches and trigger an access refresh. Verify the search text and selection remain intact and scroll restoration works. Controls rebuilt after a ranking change rely on normal Swing focus traversal, not forced focus restoration.

9. For v1.07.0, compare FPS/frame time with Farming Helper disabled and enabled at dense hops, adjacent allotments, grapevines and seaweed. Pan, rotate and zoom the camera; one outline should follow only the active physical plot. Verify watering, compost, disease, tree payment, leprechaun arrows, boat/rope, birdhouse and bank interactions. Hop worlds, change planes and stop/restart the guide; no old outlines should survive. Do not claim a measured FPS gain without live measurements.

10. In v1.07.1, visit an actual dead hops field (Jute, Wildblood or another species), with Guide anyway, Harvest / clear and Skip patch preferences. Check that dead crops clear before planting and that clear-only completes on verified empty soil. Test a dead plot after a prior completion or watering detour, adjacent allotments, permanent flowers and living Guild contracts. Unknown or invalid state must not claim completion. Verify no unintended replanting of living crops.

11. In v1.07.2, observe bank and vault, relog, compare last-seen warnings, transfer full stacks in both directions and check confirmed empty storage. With a carried box, click Fill then walk/examine, empty to bank, move two different stacks rapidly, check contents, plant from loose inventory and directly from the box, and change character. Compare every displayed quantity with native inventory. Exercise every vault view with deposits/withdrawals, search, resize, and category switching; unsupported geometry must remain native. No unverified counts should satisfy requirements.

12. In v1.07.3, enable/disable and rapidly re-enable the helper during a guide, bank and vault views; hop, log out, switch profiles and check stale sidebar updates. Replace the helper route with a personal route, stop, and verify the personal route survives. A legacy integration without ownership replies may retain the last route; clear it manually. The modern API cannot distinguish identical unmodified destinations created by different plugins, and shutdown after event-bus unregistration may leave a route rather than clear it unsafely.

13. In v1.07.4, reorder, add, remove and reset crop priorities; tab through the retained controls; toggle a patch without losing its checkbox or edit control; change filters/grouping/access and compare all guide sections at narrow and wide sidebar widths. Verify the toolbar survives same-session guide updates, new runs reset it, and preparation buttons stay pinned. Recheck disease outside the selected run, watering detours, manual subsets, staged birdhouses and all seedless/preservation policies.
