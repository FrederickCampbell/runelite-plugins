package com.farmingpatchadvisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorWavePlannerTest
{
    private static List<FarmRunPatch> patches(PatchType type, int count)
    {
        List<FarmRunPatch> out = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            out.add(new FarmRunPatch("Location " + i, "", type));
        }
        return out;
    }

    @Test
    public void breaksLongRunIntoMultipleWavesWithoutDuplicatingSaplings()
    {
        Crop oak = CropCatalog.findByName(PatchType.TREE, "Oak sapling");
        Map<Integer, Integer> stock = new HashMap<>();
        stock.put(oak.getItemId(), 12);
        DirectorWavePlanner.Result result = DirectorWavePlanner.split(
            patches(PatchType.TREE, 12), 80, stock, false, "", "", 2, 0, 2,
            false, id -> false, Collections.emptySet(), Collections.emptySet(),
            Collections.emptyMap(), false, 3);
        assertEquals(4, result.waves.size());
        assertEquals(12, result.totalStops);
        assertTrue(result.unplanned.isEmpty());
        int allocated = 0;
        for (DirectorPlanner.Plan wave : result.waves)
        {
            assertEquals(3, wave.steps.size());
            allocated += wave.seeds.get(oak.getItemId());
        }
        assertEquals(12, allocated);
    }

    @Test
    public void itemSlotBudgetCausesBankingWavesAndAllStopsRemain()
    {
        Crop oak = CropCatalog.findByName(PatchType.TREE, "Oak sapling");
        Map<Integer, Integer> stock = new HashMap<>();
        stock.put(oak.getItemId(), 6);
        DirectorWavePlanner.Result result = DirectorWavePlanner.split(
            patches(PatchType.TREE, 6), 80, stock, false, "", "", 2, 0, 23,
            false, id -> false, Collections.emptySet(), Collections.emptySet(),
            Collections.emptyMap(), false, 28);
        assertEquals(2, result.waves.size());
        assertEquals(6, result.totalStops);
        assertTrue(result.unplanned.isEmpty());
        assertEquals(3, result.waves.get(0).steps.size());
    }

    @Test
    public void missingStockIsNeverInventedOrMarkedComplete()
    {
        Crop ranarr = CropCatalog.findByName(PatchType.HERB, "Ranarr");
        Map<Integer, Integer> stock = Collections.singletonMap(ranarr.getItemId(), 1);
        DirectorWavePlanner.Result result = DirectorWavePlanner.split(
            patches(PatchType.HERB, 3), 80, stock, true, "Ranarr", "", 3, 0, 8,
            false, id -> true, Collections.emptySet(), Collections.emptySet(),
            Collections.emptyMap(), false, 3);
        assertEquals(1, result.totalStops);
        assertEquals(2, result.unplanned.size());
    }

    @Test
    public void alreadyPlantedProtectionStopsRemainInSegments()
    {
        List<FarmRunPatch> trees = patches(PatchType.TREE, 2);
        DirectorWavePlanner.Result result = DirectorWavePlanner.split(
            trees, 80, Collections.emptyMap(), false, "", "", 2, 0, 2,
            false, id -> true,
            Collections.singleton(FarmRouteManager.key(trees.get(0))),
            Collections.emptySet(), Collections.emptyMap(), false, 1);
        assertEquals(1, result.totalStops);
        assertEquals(1, result.unplanned.size());
        assertFalse(result.waves.get(0).steps.get(0).requiresPlanting);
    }
}
