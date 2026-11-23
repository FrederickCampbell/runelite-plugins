package com.farmingpatchadvisor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorTeleportPlanTest
{
    @Test
    public void countsConsumableUsesPerWaveNotEveryPatchInSameBankArea()
    {
        FarmRunPatch a = new FarmRunPatch("Falador", "", PatchType.HERB);
        FarmRunPatch b = new FarmRunPatch("Falador", "", PatchType.ALLOTMENT);
        FarmRunPatch c = new FarmRunPatch("Catherby", "", PatchType.HERB);
        DirectorPlanner.Plan first = new DirectorPlanner.Plan(Arrays.asList(
            new DirectorPlanner.Step(a, null, false),
            new DirectorPlanner.Step(b, null, false),
            new DirectorPlanner.Step(c, null, false)),
            Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyList(), 1, 1);
        DirectorWavePlanner.Result waves = new DirectorWavePlanner.Result(
            Collections.singletonList(first), Collections.emptyList(),
            Collections.emptyList());
        Map<String,RouteTeleport> selected = new HashMap<>();
        selected.put(FarmRouteManager.key(a), RouteTeleport.FALADOR);
        selected.put(FarmRouteManager.key(b), RouteTeleport.FALADOR);
        selected.put(FarmRouteManager.key(c), RouteTeleport.CATHERBY);
        Map<RouteTeleport,Integer> req =
            DirectorTeleportPlan.perWave(waves, selected).get(0);
        assertEquals(1, req.get(RouteTeleport.FALADOR).intValue());
        assertEquals(1, req.get(RouteTeleport.CATHERBY).intValue());
    }

    @Test
    public void detectsNotEnoughConsumableTeleportTablets()
    {
        Map<RouteTeleport,Integer> need =
            Collections.singletonMap(RouteTeleport.FALADOR, 3);
        assertEquals(1,
            DirectorTeleportPlan.missingTravelItems(need, Collections.emptyMap()).size());
    }
}
