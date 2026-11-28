# v1.12 candidate validation

Baseline: v1.11.6, 0ab13686110ccf3b38e685668f830f4651572196.
Development branch only. The final CI run, not this document, is the authoritative record of final test/benchmark status.

## Engineering checkpoints

- 1: c37771437746fdf4f9b83b5e6cc8a3c390b36ecf — full-run planning and protected route; 294 tests passed.
- 2: 0d762542a916f9a5e428646dd28fee8e83e4b4d6 — initial Guild workflow and watered seedling accounting; 296 tests passed.
- 3: c88e255ff88642b8a017af75f2bb7f1bf9d22a82 — Guild supply partition, sequential estimates, compost preferences, storage guidance and priority; 298 tests passed, 10,872 maintained Java lines vs 10,623 baseline (both 75 production files; 3,730 inherited lines).
- 4: candidate metadata, local installer, class constant-pool screening, comparable baseline probes, and additional route fixtures. The comparison intentionally uses different outputs for the removed prefix-fitting operation and the full-demand operation; it is not a claim about full client frame time.

## Compliance scope

Review sources: [Plugin Hub requirements](https://github.com/runelite/plugin-hub/blob/master/README.md), [review process](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review), [rejected features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features), [Jagex guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1). Outstanding PR #21369 reviewer comment specifically prohibited automated input, including Robot and KeyboardFocusManager. The existing source gate and added compiled constant-pool gate screen for these and other prohibited references. Dependencies are unchanged. The scans do not certify behavior or guarantee Hub approval.

New gameplay code reads observations and renders advisory UI only; bank/vault item actions, farming, travel and collection remain manual. The hover text overlay does not change widget contents or listeners. Existing Bank Tags and Seed Vault presentation calls remain review-sensitive. Shortest Path queries use its existing public message integration; no future-inventory simulation is claimed.

## Outstanding acceptance and limitations

The candidate is not a final publication. All live gameplay remains unverified. Full real-client end-to-end maximum, intermediate Ironman and every premade-run scenario have not been executed. Test the Guild arrival/seedling/local/departure sequence, contracts (especially completing rather than replacing a matching living crop), exact local/external shared-tool and noted-payment display, hidden preparation sections, spontaneous mushroom visit, refill after emptying a mature bin, storage availability at specialty destinations, voluntary banking, full inventory, world/profile switches, and Fossil Island ordering. Local tool returns are advisory, not an enforced step. Compost materials are a conservative supported subset, not an exhaustive catalogue; bottomless bucket type/capacity still needs player confirmation. Check seedling maturity, actual source quantities and bank priority/layout in the live client. Sequential transport recommendations are bounded independent queries and may require fallback. No final master merge, Hub PR update or release publication is authorized.
