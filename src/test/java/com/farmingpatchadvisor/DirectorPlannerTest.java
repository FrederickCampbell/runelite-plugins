package com.farmingpatchadvisor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorPlannerTest
{
    private static List<FarmRunPatch> patches(PatchType type, int count)
    {
        java.util.ArrayList<FarmRunPatch> list = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            list.add(new FarmRunPatch("Test " + i, "", type));
        }
        return list;
    }

    private static Crop crop(PatchType type, String name)
    {
        return CropCatalog.crops(type).stream()
            .filter(c -> DirectorPlanner.normalize(c.getName()).equalsIgnoreCase(name))
            .findFirst().orElseThrow(AssertionError::new);
    }

    @Test
    public void customPriorityUsesNextSeedWhenFirstRunsOut()
    {
        Crop ranarr = crop(PatchType.HERB, "ranarr");
        Crop snapdragon = crop(PatchType.HERB, "snapdragon");
        Map<Integer, Integer> stock = new HashMap<>();
        stock.put(ranarr.getItemId(), 2);
        stock.put(snapdragon.getItemId(), 4);
        DirectorPlanner.Plan plan = DirectorPlanner.build(PatchType.HERB,
            patches(PatchType.HERB, 6), 80, stock, true,
            "Ranarr,Snapdragon", "", 3, 0, 8, false, id -> true);
        assertEquals(6, plan.steps.size());
        assertEquals(2, plan.seeds.get(ranarr.getItemId()).intValue());
        assertEquals(4, plan.seeds.get(snapdragon.getItemId()).intValue());
        assertEquals(ranarr, plan.steps.get(0).crop);
        assertEquals(snapdragon, plan.steps.get(2).crop);
    }

    @Test
    public void reservesAreNeverSpent()
    {
        Crop ranarr = crop(PatchType.HERB, "ranarr");
        Crop snapdragon = crop(PatchType.HERB, "snapdragon");
        Map<Integer, Integer> stock = new HashMap<>();
        stock.put(ranarr.getItemId(), 4);
        stock.put(snapdragon.getItemId(), 5);
        DirectorPlanner.Plan plan = DirectorPlanner.build(PatchType.HERB,
            patches(PatchType.HERB, 4), 80, stock, true,
            "Ranarr,Snapdragon", "ranarr=3", 3, 0, 8, false, id -> true);
        assertEquals(1, plan.seeds.get(ranarr.getItemId()).intValue());
        assertEquals(3, plan.seeds.get(snapdragon.getItemId()).intValue());
    }

    @Test
    public void lowestEligibleFallbackHonorsFarmingLevel()
    {
        Crop guam = crop(PatchType.HERB, "guam");
        Crop ranarr = crop(PatchType.HERB, "ranarr");
        Map<Integer, Integer> stock = new HashMap<>();
        stock.put(guam.getItemId(), 3);
        stock.put(ranarr.getItemId(), 3);
        DirectorPlanner.Plan plan = DirectorPlanner.build(PatchType.HERB,
            patches(PatchType.HERB, 3), 20, stock, false,
            "", "", 3, 0, 8, false, id -> true);
        assertEquals(3, plan.steps.size());
        assertEquals(guam, plan.steps.get(0).crop);
    }

    @Test
    public void doesNotInventInventoryWhenSeedsAreMissing()
    {
        DirectorPlanner.Plan plan = DirectorPlanner.build(PatchType.HERB,
            patches(PatchType.HERB, 2), 99, new HashMap<>(), false,
            "", "", 3, 0, 8, false, id -> true);
        assertTrue(plan.steps.isEmpty());
        assertFalse(plan.warnings.isEmpty());
    }

    @Test
    public void treesRespectNonStackingAndTwentyEightSlotLimit()
    {
        Crop oak = crop(PatchType.TREE, "oak");
        Map<Integer, Integer> stock = new HashMap<>();
        stock.put(oak.getItemId(), 10);
        DirectorPlanner.Plan plan = DirectorPlanner.build(PatchType.TREE,
            patches(PatchType.TREE, 7), 60, stock, false,
            "", "", 3, 0, 22, false, id -> false);
        assertEquals(3, plan.steps.size());
        assertEquals(6, plan.occupiedSlots);
        assertTrue(plan.occupiedSlots + plan.reservedSlots <= 28);
    }

    @Test
    public void treeSeedsCannotPretendToBeSaplings()
    {
        Crop oak = crop(PatchType.TREE, "oak");
        Map<Integer, Integer> stock = new HashMap<>();
        // Nothing in the sapling ID; planner never confuses the raw seed with it.
        stock.put(999999, 100);
        DirectorPlanner.Plan plan = DirectorPlanner.build(PatchType.TREE,
            patches(PatchType.TREE, 1), 99, stock, false,
            "", "", 3, 0, 2, false, id -> true);
        assertTrue(plan.steps.isEmpty());
        assertNotEquals(999999, oak.getItemId());
    }

    @Test
    public void alreadyGrowingTreeCanRequestProtectionWithoutAnotherSapling()
    {
        List<FarmRunPatch> stops = patches(PatchType.TREE, 2);
        java.util.Set<String> preplanted = java.util.Collections.singleton(
            FarmRouteManager.key(stops.get(0)));
        Crop oak = crop(PatchType.TREE, "oak");
        java.util.Map<Integer, Integer> stock = new HashMap<>();
        stock.put(oak.getItemId(), 1);
        DirectorPlanner.Plan plan = DirectorPlanner.build(PatchType.TREE, stops, 30,
            stock, false, "", "", 3, 0, 2, false, id -> false, preplanted);
        assertEquals(2, plan.steps.size());
        assertFalse(plan.steps.get(0).requiresPlanting);
        assertNull(plan.steps.get(0).crop);
        assertEquals(1, plan.seeds.get(oak.getItemId()).intValue());
    }

    @Test
    public void malformedReserveValuesAreIgnored()
    {
        assertEquals(5, DirectorPlanner.parseReserves("ranarr=5,snapdragon=abc")
            .get("ranarr").intValue());
        assertFalse(DirectorPlanner.parseReserves("snapdragon=abc").containsKey("snapdragon"));
    }

    @Test
    public void mixedRunRetainsIndependentCropTypesInOneStockBudget()
    {
        java.util.List<FarmRunPatch> patches = Arrays.asList(
            new FarmRunPatch("Ardougne", "", PatchType.HERB),
            new FarmRunPatch("Falador", "", PatchType.TREE),
            new FarmRunPatch("Ardougne", "North", PatchType.ALLOTMENT));
        Crop herb = crop(PatchType.HERB, "ranarr");
        Crop tree = crop(PatchType.TREE, "oak");
        Crop allotment = crop(PatchType.ALLOTMENT, "watermelon");
        java.util.Map<Integer,Integer> owned = new HashMap<>();
        owned.put(herb.getItemId(), 1);
        owned.put(tree.getItemId(), 1);
        owned.put(allotment.getItemId(), 3);
        DirectorPlanner.Plan plan = DirectorPlanner.build(null, patches, 80,
            owned, false, "", "", 3, 0, 6, false,
            id -> id != tree.getItemId());
        assertEquals(3, plan.steps.size());
        assertEquals(1, plan.seeds.get(tree.getItemId()).intValue());
        assertEquals(3, plan.seeds.get(allotment.getItemId()).intValue());
        assertEquals(6, plan.occupiedSlots); // tools + herb + tree sapling + watermelon stack
    }

    @Test
    public void treeFamilyUsesCheckHealthAndProtection()
    {
        assertTrue(DirectorStepEngine.isTreeType(PatchType.FRUIT_TREE));
        assertTrue(DirectorStepEngine.isTreeType(PatchType.HARDWOOD_TREE));
        assertFalse(DirectorStepEngine.isTreeType(PatchType.BUSH));
    }

    @Test
    public void harvestReadyPatchRemainsVisitableWithoutReplacementStock()
    {
        FarmRunPatch patch = new FarmRunPatch("Falador", "", PatchType.HERB);
        java.util.Set<String> ready = java.util.Collections.singleton(
            FarmRouteManager.key(patch));
        DirectorPlanner.Plan plan = DirectorPlanner.build(null,
            java.util.Collections.singletonList(patch), 99,
            java.util.Collections.emptyMap(), false, "", "", 3, 0, 8,
            false, id -> true, java.util.Collections.emptySet(), ready,
            java.util.Collections.emptyMap());
        assertEquals(1, plan.steps.size());
        assertTrue(plan.steps.get(0).harvestOnly);
        assertFalse(plan.steps.get(0).requiresPlanting);
        assertNull(plan.steps.get(0).crop);
    }

    @Test
    public void manualCropOverrideUsesRequestedCropIfAvailable()
    {
        Crop ranarr = crop(PatchType.HERB, "ranarr");
        Crop snapdragon = crop(PatchType.HERB, "snapdragon");
        FarmRunPatch patch = new FarmRunPatch("Falador", "", PatchType.HERB);
        java.util.Map<Integer,Integer> stock = new HashMap<>();
        stock.put(ranarr.getItemId(), 3);
        stock.put(snapdragon.getItemId(), 3);
        java.util.Map<String,String> override = java.util.Collections.singletonMap(
            FarmRouteManager.key(patch), "Ranarr");
        DirectorPlanner.Plan plan = DirectorPlanner.build(null,
            java.util.Collections.singletonList(patch), 99, stock, false,
            "", "", 3, 0, 8, false, id -> true,
            java.util.Collections.emptySet(), java.util.Collections.emptySet(), override);
        assertEquals(ranarr, plan.steps.get(0).crop);
    }

    @Test
    public void herblorePresetPrefersRanarrAndDoesNotRestrictTrees()
    {
        Crop ranarr = crop(PatchType.HERB, "ranarr");
        Crop oak = crop(PatchType.TREE, "oak");
        Crop snapdragon = crop(PatchType.HERB, "snapdragon");
        java.util.Map<Integer,Integer> stock = new HashMap<>();
        stock.put(ranarr.getItemId(), 1);
        stock.put(snapdragon.getItemId(), 1);
        stock.put(oak.getItemId(), 1);
        java.util.List<FarmRunPatch> candidates = Arrays.asList(
            new FarmRunPatch("Falador", "", PatchType.HERB),
            new FarmRunPatch("Falador", "", PatchType.TREE));
        DirectorPlanner.Plan plan = DirectorPlanner.build(null, candidates, 80,
            stock, false, "", "", 2, 0, 2, false, id -> true,
            java.util.Collections.emptySet(), java.util.Collections.emptySet(),
            java.util.Collections.emptyMap(), true);
        assertEquals(2, plan.steps.size());
        assertEquals(ranarr, plan.steps.get(0).crop);
        assertEquals(oak, plan.steps.get(1).crop);
    }

    @Test
    public void perPatchOverrideTakesPrecedenceOverGeneralCustomPriority()
    {
        Crop ranarr = crop(PatchType.HERB, "ranarr");
        Crop snapdragon = crop(PatchType.HERB, "snapdragon");
        FarmRunPatch patch = new FarmRunPatch("Falador", "", PatchType.HERB);
        java.util.Map<Integer,Integer> stock = new HashMap<>();
        stock.put(ranarr.getItemId(), 1);
        stock.put(snapdragon.getItemId(), 1);
        DirectorPlanner.Plan plan = DirectorPlanner.build(null,
            java.util.Collections.singletonList(patch), 80, stock, true,
            "Snapdragon", "", 2, 0, 2, false, id -> true,
            java.util.Collections.emptySet(), java.util.Collections.emptySet(),
            java.util.Collections.singletonMap(FarmRouteManager.key(patch), "Ranarr"), false);
        assertEquals(ranarr, plan.steps.get(0).crop);
    }

    @Test
    public void teleportSlotsAreCountedOnlyForSelectedStops()
    {
        Crop oak = crop(PatchType.TREE, "oak");
        Map<Integer,Integer> stock = new HashMap<>();
        stock.put(oak.getItemId(), 10);
        List<FarmRunPatch> candidates = patches(PatchType.TREE, 10);
        DirectorPlanner.Plan plan = DirectorPlanner.build(PatchType.TREE,
            candidates, 75, stock, false, "", "", 2, 0, 23,
            false, id -> false, java.util.Collections.emptySet(),
            java.util.Collections.emptySet(), java.util.Collections.emptyMap(),
            false, 10, selected -> selected.isEmpty() ? 0 : 1);
        assertEquals(2, plan.steps.size()); // 28-23-2 tools-1 teleport = 2
        assertEquals(5, plan.occupiedSlots);
    }
}
