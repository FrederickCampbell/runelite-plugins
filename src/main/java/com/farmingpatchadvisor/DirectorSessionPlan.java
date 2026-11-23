package com.farmingpatchadvisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One immutable client-thread planning snapshot for the Guide. */
final class DirectorSessionPlan
{
    final List<DirectorPlanner.Plan> waves;
    final DirectorStockLedger observedStock;
    final List<String> warnings;
    final int unplanned;
    final int scheduled;

    DirectorSessionPlan(DirectorWavePlanner.Result result, DirectorStockLedger stock,
                        List<String> warnings)
    {
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
