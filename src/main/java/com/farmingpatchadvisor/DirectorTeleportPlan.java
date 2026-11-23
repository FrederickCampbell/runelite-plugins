package com.farmingpatchadvisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Distinct route supplies, with consumable uses counted per bank-to-bank wave. */
final class DirectorTeleportPlan
{
    private DirectorTeleportPlan() {}

    static List<Map<RouteTeleport, Integer>> perWave(DirectorWavePlanner.Result result,
                                                     Map<String, RouteTeleport> choices)
    {
        List<Map<RouteTeleport, Integer>> output = new ArrayList<>();
        for (DirectorPlanner.Plan wave : result.waves)
        {
            List<FarmRunPatch> steps = new ArrayList<>();
            for (DirectorPlanner.Step step : wave.steps) steps.add(step.patch);
            output.add(Collections.unmodifiableMap(
                FarmRouteManager.requirements(steps, choices)));
        }
        return Collections.unmodifiableList(output);
    }

    static Map<RouteTeleport, Integer> totalConsumableUses(
        List<Map<RouteTeleport, Integer>> waves)
    {
        Map<RouteTeleport, Integer> result = new LinkedHashMap<>();
        for (Map<RouteTeleport, Integer> wave : waves)
        {
            for (Map.Entry<RouteTeleport, Integer> e : wave.entrySet())
            {
                if (e.getKey().consumable)
                {
                    result.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
        }
        return result;
    }

    static Map<RouteTeleport, Integer> totalRequirements(
        List<Map<RouteTeleport, Integer>> waves)
    {
        Map<RouteTeleport, Integer> totals = new LinkedHashMap<>();
        for (Map<RouteTeleport, Integer> wave : waves)
        {
            for (Map.Entry<RouteTeleport, Integer> e : wave.entrySet())
            {
                if (e.getKey().consumable)
                {
                    totals.merge(e.getKey(), e.getValue(), Integer::sum);
                }
                else
                {
                    totals.merge(e.getKey(), 1, Math::max);
                }
            }
        }
        return totals;
    }

    /** Reusable equipped items occupy no bag slot; unstackable consumables
     * consume a slot for every use, rather than one slot per teleport kind. */
    static int inventorySlots(Map<RouteTeleport, Integer> uses, Map<Integer, Integer> equipped,
                              java.util.function.IntPredicate stackable)
    {
        int slots = 0;
        for (Map.Entry<RouteTeleport, Integer> entry : uses.entrySet())
        {
            RouteTeleport teleport = entry.getKey();
            if (teleport == RouteTeleport.NONE || entry.getValue() <= 0) continue;
            int[] ids = teleport.itemIds();
            boolean worn = false;
            for (int id : ids) worn |= equipped.getOrDefault(id, 0) > 0;
            if (!teleport.consumable && worn) continue;
            slots += teleport.consumable && ids.length > 0 && !stackable.test(ids[0])
                ? entry.getValue() : 1;
        }
        return slots;
    }

    static List<String> missingTravelItems(
        Map<RouteTeleport, Integer> uses, Map<Integer, Integer> available)
    {
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<RouteTeleport, Integer> item : uses.entrySet())
        {
            RouteTeleport teleport = item.getKey();
            int owned = 0;
            for (int id : teleport.itemIds())
            {
                owned += Math.max(0, available.getOrDefault(id, 0));
            }
            int required = teleport.consumable ? item.getValue() : 1;
            if (owned < required)
            {
                warnings.add(teleport.toString() + ": need " + required +
                    " use(s), scanned supplies contain " + owned +
                    ". Charges on reusable jewelry are not verified.");
            }
        }
        return warnings;
    }
}
