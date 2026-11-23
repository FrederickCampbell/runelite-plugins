package com.farminghelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class SuppliesTest
{
    @Test public void ordinaryCompostConsumesOneSlotPerPatchAndToolsAreShared()
    {
        java.util.List<FarmRunPatch> patches = Arrays.asList(
            new FarmRunPatch("Falador", "", PatchType.HERB),
            new FarmRunPatch("Catherby", "", PatchType.HERB));
        Map<Integer, Integer> kit = Supplies.required(patches, true, false, false);
        assertEquals(Integer.valueOf(2), kit.get(ItemID.BUCKET_ULTRACOMPOST));
        assertEquals(Integer.valueOf(1), kit.get(ItemID.DIBBER));
        assertEquals(5, Supplies.slots(patches, kit, id -> false));
    }

    @Test public void bottomlessBucketUsesOneSlotWithoutPretendingToKnowCharges()
    {
        java.util.List<FarmRunPatch> patches = Arrays.asList(
            new FarmRunPatch("Falador", "", PatchType.HERB),
            new FarmRunPatch("Varrock", "", PatchType.TREE));
        Map<Integer, Integer> kit = Supplies.required(patches, true, true, false);
        assertFalse(kit.containsKey(ItemID.BUCKET_ULTRACOMPOST));
        assertEquals(Integer.valueOf(1), kit.get(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED));
        assertEquals(5, Supplies.slots(patches, kit, id -> false));
    }

    @Test public void missingSuppliesAppearInSourceAwareManifest()
    {
        RunPlanner.Plan wave = new RunPlanner.Plan(Collections.emptyList(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), 0, 0);
        StockLedger ledger = new StockLedger(
            Collections.singletonMap(ItemID.RAKE, 1),
            Collections.singletonMap(ItemID.SPADE, 1), Collections.emptyMap(), true, false);
        Map<Integer, Integer> kit = Supplies.required(Collections.singletonList(
            new FarmRunPatch("Falador", "", PatchType.HERB)), true, false, false);
        StockLedger.Manifest manifest = ledger.manifest(wave, kit);
        assertEquals(Integer.valueOf(1), manifest.takeFromBank.get(ItemID.SPADE));
        assertEquals(Integer.valueOf(1), manifest.missing.get(ItemID.BUCKET_ULTRACOMPOST));
        assertEquals(Integer.valueOf(1), manifest.carried.get(ItemID.RAKE));
    }
}
