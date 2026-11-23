package com.farmingpatchadvisor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorBankChecklistTest
{
    @Test public void onlyRemainingPlannedCropsAndTravelArePublished()
    {
        Crop ranarr = CropCatalog.findByName(PatchType.HERB, "Ranarr");
        Crop snap = CropCatalog.findByName(PatchType.HERB, "Snapdragon");
        Map<Integer,Integer> stock = new HashMap<>();
        stock.put(ranarr.getItemId(), 1);
        stock.put(snap.getItemId(), 1);
        DirectorPlanner.Plan wave = DirectorPlanner.build(PatchType.HERB,
            Arrays.asList(new FarmRunPatch("Falador", "", PatchType.HERB),
                new FarmRunPatch("Catherby", "", PatchType.HERB)),
            99, stock, true, "Ranarr,Snapdragon", "", 2, 0, 8, false, id -> true);
        DirectorBankChecklist view = DirectorBankChecklist.remaining(wave,
            Collections.emptyMap(), Collections.singletonMap(RouteTeleport.CAMELOT, 1),
            Collections.singleton(0));
        assertFalse(view.ids().contains(ranarr.getItemId()));
        assertTrue(view.ids().contains(snap.getItemId()));
        assertTrue(view.ids().contains(RouteTeleport.CAMELOT.itemIds()[0]));
    }
}
