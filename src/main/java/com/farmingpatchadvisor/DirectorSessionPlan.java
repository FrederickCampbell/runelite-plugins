package com.farmingpatchadvisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** One immutable client-thread planning snapshot for the Guide. */
final class DirectorSessionPlan
{
    final List<DirectorPlanner.Plan> waves;
    final DirectorStockLedger observedStock;
    final List<String> warnings;
    final int unplanned;
    final int scheduled;
    final List<Map<RouteTeleport, Integer>> teleports;
    final List<Map<Integer, Integer>> supplies;
    final Map<String, RouteTeleport> teleportByPatch;

    DirectorSessionPlan(DirectorWavePlanner.Result result, DirectorStockLedger stock,
                        List<String> warnings)
    {
        this(result, stock, warnings, Collections.emptyList());
    }

    DirectorSessionPlan(DirectorWavePlanner.Result result, DirectorStockLedger stock,
                        List<String> warnings, List<Map<RouteTeleport, Integer>> travel)
    {
        this(result, stock, warnings, travel, Collections.emptyList());
    }

    DirectorSessionPlan(DirectorWavePlanner.Result result, DirectorStockLedger stock,
                        List<String> warnings, List<Map<RouteTeleport, Integer>> travel,
                        List<Map<Integer, Integer>> supplies)
    {
        this(result, stock, warnings, travel, supplies, Collections.emptyMap());
    }

    DirectorSessionPlan(DirectorWavePlanner.Result result, DirectorStockLedger stock,
                        List<String> warnings, List<Map<RouteTeleport, Integer>> travel,
                        List<Map<Integer, Integer>> supplies, Map<String, RouteTeleport> teleportByPatch)
    {
        this.teleportByPatch = Collections.unmodifiableMap(new LinkedHashMap<>(teleportByPatch));
        List<Map<Integer, Integer>> frozenSupplies = new ArrayList<>();
        for (Map<Integer, Integer> req : supplies)
            frozenSupplies.add(Collections.unmodifiableMap(new LinkedHashMap<>(req)));
        this.supplies = Collections.unmodifiableList(frozenSupplies);
        List<Map<RouteTeleport, Integer>> frozen = new ArrayList<>();
        for (Map<RouteTeleport, Integer> req : travel)
        {
            frozen.add(Collections.unmodifiableMap(new LinkedHashMap<>(req)));
        }
        this.teleports = Collections.unmodifiableList(frozen);
        this.waves = Collections.unmodifiableList(new ArrayList<>(result.waves));
        this.observedStock = stock;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        this.unplanned = result.unplanned.size();
        this.scheduled = result.totalStops;
    }

    DirectorPlanner.Plan firstWave()
    {
        return waves.isEmpty()
            ? new DirectorPlanner.Plan(Collections.emptyList(), Collections.emptyMap(),
                Collections.emptyMap(), warnings, 0, 0)
            : waves.get(0);
    }
}
