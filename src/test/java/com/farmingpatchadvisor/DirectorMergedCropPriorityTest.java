package com.farmingpatchadvisor;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorMergedCropPriorityTest
{
    @Test public void mergedHerbPrioritiesDoNotDisqualifyAvailableTreeSeeds()
    {
        Crop ranarr = CropCatalog.crops(PatchType.HERB).stream()
            .filter(c -> DirectorPlanner.normalize(c.getName()).equals("ranarr"))
            .findFirst().orElseThrow(AssertionError::new);
        Crop oak = CropCatalog.crops(PatchType.TREE).stream()
            .filter(c -> DirectorPlanner.normalize(c.getName()).equals("oak"))
            .findFirst().orElseThrow(AssertionError::new);
        Map<Integer, Integer> stock = new HashMap<>();
        stock.put(ranarr.getItemId(), 1);
        stock.put(oak.getItemId(), 1);
        Map<FarmRunType, String> herbOnly = new EnumMap<>(FarmRunType.class);
        herbOnly.put(FarmRunType.HERB, "Ranarr, Snapdragon");
        DirectorPlanner.Plan plan = DirectorPlanner.build(null,
            Arrays.asList(new FarmRunPatch("Falador", "", PatchType.HERB),
                new FarmRunPatch("Falador", "", PatchType.TREE)),
            99, stock, false, "", "", 1, 0, 2, false, id -> false,
            Collections.emptySet(), Collections.emptySet(), Collections.emptyMap(),
            false, 5, selected -> 0, herbOnly);
        assertEquals(2, plan.steps.size());
        assertEquals(ranarr, plan.steps.get(0).crop);
        assertEquals(oak, plan.steps.get(1).crop);
    }
}
