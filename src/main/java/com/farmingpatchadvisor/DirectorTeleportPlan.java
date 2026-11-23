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
