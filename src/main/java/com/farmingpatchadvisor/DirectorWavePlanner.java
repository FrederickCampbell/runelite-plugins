package com.farmingpatchadvisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/**
 * Split a complete farming itinerary into loadouts. Reuses the same Advisor
 * crop ranking, availability and RuneLite item stackability calculations as
 * DirectorPlanner; consumes planned stock between waves to prevent double-use.
 *
 * This is an inventory/loadout segmentation algorithm, NOT a proven estimate
 * of harvesting yield or real-world walking time.
 */
final class DirectorWavePlanner
{
    static final class Result
    {
        final List<DirectorPlanner.Plan> waves;
        final List<FarmRunPatch> unplanned;
        final List<String> warnings;
        final int totalStops;

        Result(List<DirectorPlanner.Plan> waves, List<FarmRunPatch> unplanned,
               List<String> warnings)
        {
            this.waves = Collections.unmodifiableList(new ArrayList<>(waves));
            this.unplanned = Collections.unmodifiableList(new ArrayList<>(unplanned));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
            int count = 0;
            for (DirectorPlanner.Plan wave : waves) count += wave.steps.size();
            this.totalStops = count;
        }
    }

    private DirectorWavePlanner() {}

    static Result split(List<FarmRunPatch> patches, int farmingLevel,
                        Map<Integer, Integer> stock, boolean customPriority,
                        String priorities, String reserves, int toolSlots,
                        int teleportSlots, int harvestSlots,
                        boolean protect, IntPredicate stackable,
                        Set<String> preplanted, Set<String> harvestOnly,
                        Map<String, String> overrides, boolean herbloreFocus,
                        int maxStopsPerWave)
    {
        return split(patches, farmingLevel, stock, customPriority, priorities,
            reserves, toolSlots, teleportSlots, harvestSlots, protect, stackable,
            preplanted, harvestOnly, overrides, herbloreFocus,
            maxStopsPerWave, selected -> teleportSlots);
    }

    static Result split(List<FarmRunPatch> patches, int farmingLevel,
                        Map<Integer, Integer> stock, boolean customPriority,
                        String priorities, String reserves, int toolSlots,
                        int teleportSlots, int harvestSlots,
                        boolean protect, IntPredicate stackable,
                        Set<String> preplanted, Set<String> harvestOnly,
                        Map<String, String> overrides, boolean herbloreFocus,
                        int maxStopsPerWave,
                        ToIntFunction<List<FarmRunPatch>> teleportSlotsForSelected)
    {
        return split(patches, farmingLevel, stock, customPriority, priorities,
            reserves, toolSlots, teleportSlots, harvestSlots, protect, stackable,
            preplanted, harvestOnly, overrides, herbloreFocus, maxStopsPerWave,
            teleportSlotsForSelected, Collections.emptyMap());
    }

    static Result split(List<FarmRunPatch> patches, int farmingLevel,
                        Map<Integer, Integer> stock, boolean customPriority,
                        String priorities, String reserves, int toolSlots,
                        int teleportSlots, int harvestSlots,
                        boolean protect, IntPredicate stackable,
                        Set<String> preplanted, Set<String> harvestOnly,
                        Map<String, String> overrides, boolean herbloreFocus,
                        int maxStopsPerWave,
                        ToIntFunction<List<FarmRunPatch>> teleportSlotsForSelected,
                        Map<FarmRunType, String> perCategoryPriorities)
    {
        return split(patches, farmingLevel, stock, customPriority, priorities, reserves, toolSlots,
            teleportSlots, harvestSlots, protect, stackable, preplanted, harvestOnly, overrides,
            herbloreFocus, maxStopsPerWave, teleportSlotsForSelected, perCategoryPriorities,
            Collections.emptyMap());
    }

    static Result split(List<FarmRunPatch> patches, int farmingLevel,
                        Map<Integer, Integer> stock, boolean customPriority,
                        String priorities, String reserves, int toolSlots,
                        int teleportSlots, int harvestSlots, boolean protect, IntPredicate stackable,
                        Set<String> preplanted, Set<String> harvestOnly,
                        Map<String, String> overrides, boolean herbloreFocus, int maxStopsPerWave,
                        ToIntFunction<List<FarmRunPatch>> teleportSlotsForSelected,
                        Map<FarmRunType, String> perCategoryPriorities,
                        Map<PatchType, DirectorPatchPriority> patchPriorities)
    {
        List<FarmRunPatch> remaining = new ArrayList<>(patches);
        Map<Integer, Integer> available = new HashMap<>(stock);
        List<DirectorPlanner.Plan> waves = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int maxStops = Math.max(1, Math.min(28, maxStopsPerWave));
        int guard = patches.size() + 1;
        while (!remaining.isEmpty() && guard-- > 0)
        {
            DirectorPlanner.Plan wave = DirectorPlanner.build(null, remaining,
                farmingLevel, available, customPriority, priorities, reserves,
                toolSlots, teleportSlots, harvestSlots, protect, stackable,
                preplanted, harvestOnly, overrides, herbloreFocus, maxStops,
                teleportSlotsForSelected, perCategoryPriorities, patchPriorities);
            if (wave.steps.isEmpty())
            {
                warnings.addAll(wave.warnings);
                break;
            }
            waves.add(wave);
            warnings.addAll(wave.warnings);
            Set<String> planned = new HashSet<>();
            for (DirectorPlanner.Step step : wave.steps)
            {
                planned.add(FarmRouteManager.key(step.patch));
            }
            remaining.removeIf(patch -> planned.contains(FarmRouteManager.key(patch)));
            debit(available, wave.seeds);
            // Gardener payments are conditional but reserve stock across all waves.
            // Missing payments are presented as shortfalls in the loadout manifest.
            debit(available, wave.payments);
        }
        if (!remaining.isEmpty())
        {
            warnings.add(remaining.size() +
                " patch(es) cannot be covered with scanned planting stock or selected "
                + "loadout limits. Restock/replan; they were NOT marked completed.");
        }
        if (waves.size() > 1)
        {
            warnings.add("Requires " + waves.size() +
                " inventory waves. Bank between waves and verify harvest space.");
        }
        return new Result(waves, remaining, warnings);
    }

    private static void debit(Map<Integer, Integer> available,
                              Map<Integer, Integer> requirements)
    {
        for (Map.Entry<Integer, Integer> entry : requirements.entrySet())
        {
            available.merge(entry.getKey(), -entry.getValue(), Integer::sum);
            if (available.get(entry.getKey()) < 0) available.put(entry.getKey(), 0);
        }
    }
}
