package com.farminghelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class TeleportPlanTest
{
    @Test
    public void countsConsumableUsesPerWaveNotEveryPatchInSameBankArea()
    {
        FarmRunPatch a = new FarmRunPatch("Falador", "", PatchType.HERB);
        FarmRunPatch b = new FarmRunPatch("Falador", "", PatchType.ALLOTMENT);
        FarmRunPatch c = new FarmRunPatch("Catherby", "", PatchType.HERB);
        RunPlanner.Plan first = new RunPlanner.Plan(Arrays.asList(
            new RunPlanner.Step(a, null, false),
            new RunPlanner.Step(b, null, false),
            new RunPlanner.Step(c, null, false)),
            Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyList(), 1, 1);
        WavePlanner.Result waves = new WavePlanner.Result(
            Collections.singletonList(first), Collections.emptyList(),
            Collections.emptyList());
        Map<String,RouteTeleport> selected = new HashMap<>();
        selected.put(FarmRouteManager.key(a), RouteTeleport.FALADOR);
        selected.put(FarmRouteManager.key(b), RouteTeleport.FALADOR);
        selected.put(FarmRouteManager.key(c), RouteTeleport.CATHERBY);
        Map<RouteTeleport,Integer> req =
            TeleportPlan.perWave(waves, selected).get(0);
        assertEquals(1, req.get(RouteTeleport.FALADOR).intValue());
        assertEquals(1, req.get(RouteTeleport.CATHERBY).intValue());
    }

    @Test
    public void detectsNotEnoughConsumableTeleportTablets()
    {
        Map<RouteTeleport,Integer> need =
            Collections.singletonMap(RouteTeleport.FALADOR, 3);
        assertEquals(1,
            TeleportPlan.missingTravelItems(need, Collections.emptyMap()).size());
    }
}
