package com.farmingpatchadvisor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorHardeningTest
{
    @Test public void forwardJumpDoesNotLoseEarlierStops()
    {
        assertEquals(0, DirectorProgress.nextUnfinished(3, 2,
            Collections.singleton(1)));
        assertEquals(3, DirectorProgress.nextUnfinished(3, 2,
            new HashSet<>(Arrays.asList(0, 1))));
    }

    @Test public void harvestAndProtectionOnlyStillRequireTravelSpace()
    {
        FarmRunPatch herb = new FarmRunPatch("Falador", "", PatchType.HERB);
        FarmRunPatch tree = new FarmRunPatch("Varrock", "", PatchType.TREE);
        DirectorPlanner.Plan plan = DirectorPlanner.build(null, Arrays.asList(herb, tree),
            99, new HashMap<>(), false, "", "", 25, 0, 2, true, id -> true,
            Collections.singleton(FarmRouteManager.key(tree)),
            Collections.singleton(FarmRouteManager.key(herb)), Collections.emptyMap(),
            false, 8, selected -> selected.size() * 2);
        assertTrue(plan.steps.isEmpty());
    }

    @Test public void impossibleFirstStopDoesNotBlockLaterHarvest()
    {
        FarmRunPatch tree = new FarmRunPatch("Varrock", "", PatchType.TREE);
        FarmRunPatch herb = new FarmRunPatch("Falador", "", PatchType.HERB);
        DirectorPlanner.Plan plan = DirectorPlanner.build(null, Arrays.asList(tree, herb),
            99, new HashMap<>(), false, "", "", 25, 0, 2, true, id -> true,
            Collections.singleton(FarmRouteManager.key(tree)),
            Collections.singleton(FarmRouteManager.key(herb)), Collections.emptyMap(),
            false, 8, selected -> selected.contains(tree) ? 2 : 1);
        assertEquals(1, plan.steps.size());
        assertEquals(herb, plan.steps.get(0).patch);
        assertTrue(plan.occupiedSlots + plan.reservedSlots <= 28);
    }
}
