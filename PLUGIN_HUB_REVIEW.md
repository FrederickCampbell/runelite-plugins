# Farming Helper review and live-client checks

The player performs all game actions. The plugin reads farming observations, displays instructions, and changes the presentation of opt-in storage views. Shortest Path receives navigation messages. The plugin has no new runtime dependencies in v1.01. Development builds and original licenses are documented in [Development](docs/IMPLEMENTATION.md).

| Integration | Review surface |
| --- | --- |
| Seed Vault | Native item widgets retain their IDs, indices, quantities and listeners. The helper restores owned visual changes before native rebuilds and reapplies validated layout afterward. The generic scrollbar refresh runs outside native script callbacks. |
| Bank Tags | The active-run predicate admits physical IDs in the supplied temporary layout. The native Bank Tags plugin manages remembered tabs. |
| Shortest Path | The guide tracks its displayed leg. Modern ownership checks compare the reported destination before clearing. Legacy integrations have limited ownership information. |

## Live acceptance

1. In Highest XP, Top 3, Custom and Active Run, deposit a new sapling, change a seed stack, withdraw the last item and redeposit it. Check compact headers, scroll, tooltip, right-click quantities and the inventory received. Switch native categories, search, resize, close and disable the plugin.
2. In preparation, open previously unobserved bank and vault stock. Check the destination changes and the route disappears when supplies are gathered. Start with missing items and manually resupply later. Replace the helper route with a personal Shortest Path route and check ownership.
3. With Bank Tags remembering the last tab, switch between native tags and the farming shelf. Withdraw, equip, close, reopen, switch to birdhouses and disable the plugin. Inspect saved tags and layouts.
4. Verify Kastori flowers next to fruit trees, numbered vine patches, both compost-bin sizes, protected contracts, a newly actionable remote patch and a disease interruption. Check same-character hopping and character switching on specialty travel legs.

[Plugin Hub development and review](https://github.com/runelite/plugin-hub) · [RuneLite feature guidance](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) · [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
