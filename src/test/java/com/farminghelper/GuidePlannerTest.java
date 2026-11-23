package com.farminghelper;

import java.util.*;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuidePlannerTest
{
    private static RunPlanner.Plan guide(List<FarmRunPatch> patches, Map<Integer,Integer> stock,
            Map<String,String> overrides, Map<PatchType,PatchPriority> priorities)
    {
        return RunPlanner.guide(patches, 99, stock, 8, false, id -> false,
            Collections.emptySet(), Collections.emptySet(), overrides, false, p -> 3, priorities);
    }

    @Test public void missingStockAndInventoryCapacityNeverRemovePatches()
    {
        List<FarmRunPatch> patches = new ArrayList<>();
        for (int i = 0; i < 35; i++)
            patches.add(new FarmRunPatch("Location " + i, "", PatchType.TREE));
        RunPlanner.Plan plan = guide(patches, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap());
        assertEquals(35, plan.steps.size());
        assertTrue(plan.occupiedSlots > 28);
        assertEquals(35, plan.seeds.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test public void unavailableExplicitOverrideRemainsInChecklist()
    {
        FarmRunPatch patch = new FarmRunPatch("Falador", "", PatchType.HERB);
        Crop ranarr = CropCatalog.findByName(PatchType.HERB, "Ranarr");
        Crop snap = CropCatalog.findByName(PatchType.HERB, "Snapdragon");
        RunPlanner.Plan plan = guide(Collections.singletonList(patch),
            Collections.singletonMap(snap.getItemId(), 10),
            Collections.singletonMap(FarmRouteManager.key(patch), "Ranarr"),
            Collections.emptyMap());
        assertEquals(ranarr.getItemId(), plan.steps.get(0).crop.getItemId());
        assertEquals(Integer.valueOf(1), plan.seeds.get(ranarr.getItemId()));
    }

    @Test public void availablePreferredStockAllocatedBeforeMissingSuggestions()
    {
        Crop ranarr = CropCatalog.findByName(PatchType.HERB, "Ranarr");
        Crop snap = CropCatalog.findByName(PatchType.HERB, "Snapdragon");
        List<FarmRunPatch> patches = Arrays.asList(new FarmRunPatch("Falador", "", PatchType.HERB),
            new FarmRunPatch("Catherby", "", PatchType.HERB), new FarmRunPatch("Ardougne", "", PatchType.HERB));
        Map<Integer,Integer> stock = new HashMap<>();
        stock.put(ranarr.getItemId(), 1); stock.put(snap.getItemId(), 1);
        RunPlanner.Plan plan = guide(patches, stock, Collections.emptyMap(),
            Collections.singletonMap(PatchType.HERB, new PatchPriority(true, "Ranarr,Snapdragon", "")));
        assertEquals(ranarr.getItemId(), plan.steps.get(0).crop.getItemId());
        assertEquals(snap.getItemId(), plan.steps.get(1).crop.getItemId());
        assertEquals(ranarr.getItemId(), plan.steps.get(2).crop.getItemId());
        BankChecklist checklist = BankChecklist.remaining(plan, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptySet(), 0);
        assertEquals(Integer.valueOf(1), checklist.seeds.get(ranarr.getItemId()));
    }

    @Test public void unlockedToolsAreOmittedAndCompostIsRecommended()
    {
        List<FarmRunPatch> patches = Collections.singletonList(new FarmRunPatch("Falador", "", PatchType.HERB));
        Map<Integer,Integer> kit = Supplies.guideKit(patches, ItemID.BUCKET_ULTRACOMPOST, true,
            false, true, true);
        assertFalse(kit.containsKey(ItemID.RAKE));
        assertFalse(kit.containsKey(ItemID.DIBBER));
        assertTrue(kit.containsKey(ItemID.SPADE));
        assertTrue(Supplies.recommended(ItemID.BUCKET_ULTRACOMPOST));
        assertTrue(Supplies.recommended(ItemID.FAIRY_ENCHANTED_SECATEURS));
    }
}
