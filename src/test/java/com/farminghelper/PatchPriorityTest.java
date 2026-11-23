package com.farminghelper;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PatchPriorityTest
{
    private Crop crop(PatchType type, String name)
    {
        return CropCatalog.crops(type).stream().filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(name))
            .findFirst().orElseThrow(AssertionError::new);
    }

    private RunPlanner.Plan build(List<FarmRunPatch> stops, int level, Map<Integer,Integer> stock,
                                       Map<PatchType,PatchPriority> preferences)
    {
        return RunPlanner.build(null, stops, level, stock, false, "", "", 0,0,0,false,
            id -> true, Collections.emptySet(), Collections.emptySet(), Collections.emptyMap(),
            false, 28, selected -> 0, Collections.emptyMap(), preferences);
    }

    @Test public void animaAccessDoesNotPermitEarlyPlanting()
    {
        assertEquals(65, Waypoints.guildAccessLevel(PatchType.ANIMA));
        assertEquals(76, PatchPriority.plantingLevel(PatchType.ANIMA));
        Crop attas = crop(PatchType.ANIMA, "attas");
        List<FarmRunPatch> stops = Collections.singletonList(new FarmRunPatch("Farming Guild", "", PatchType.ANIMA));
        Map<Integer,Integer> stock = Collections.singletonMap(attas.getItemId(), 2);
        assertTrue(build(stops,75,stock,Collections.emptyMap()).steps.isEmpty());
        assertEquals(attas, build(stops,76,stock,Collections.emptyMap()).steps.get(0).crop);
    }

    @Test public void independentPreferencesForTypesInSameRun()
    {
        Crop fruit = crop(PatchType.FRUIT_TREE, "apple");
        Crop calquat = CropCatalog.crops(PatchType.CALQUAT).get(0);
        Map<Integer,Integer> stock = new HashMap<>();
        stock.put(fruit.getItemId(), 2); stock.put(calquat.getItemId(), 2);
        Map<PatchType,PatchPriority> preferences = new EnumMap<>(PatchType.class);
        preferences.put(PatchType.FRUIT_TREE,new PatchPriority(true,fruit.getName(),""));
        preferences.put(PatchType.CALQUAT,new PatchPriority(false,"",""));
        List<FarmRunPatch> stops = Arrays.asList(new FarmRunPatch("Fruit", "", PatchType.FRUIT_TREE),
            new FarmRunPatch("Calquat", "", PatchType.CALQUAT));
        RunPlanner.Plan result = build(stops,99,stock,preferences);
        assertEquals(2,result.steps.size());
        assertEquals(fruit,result.steps.get(0).crop);
        assertEquals(calquat,result.steps.get(1).crop);
    }

    @Test public void legacyPreferencesFilterToPhysicalType()
    {
        PatchPriority p = PatchPriority.legacy(PatchType.HERB,true,"Ranarr, Snapdragon, Oak sapling","ranarr=3, oak=2");
        assertEquals("Ranarr, Snapdragon",p.csv);
        assertEquals("ranarr=3",p.reserves);
    }

    @Test public void rankedFallbackAndReserveRemainEffective()
    {
        Crop ranarr=crop(PatchType.HERB,"ranarr"), snap=crop(PatchType.HERB,"snapdragon");
        Map<Integer,Integer> stock = new HashMap<>();
        stock.put(ranarr.getItemId(),2); stock.put(snap.getItemId(),3);
        Map<PatchType,PatchPriority> preferences = Collections.singletonMap(PatchType.HERB,
            new PatchPriority(true,"Ranarr, Snapdragon","ranarr=1"));
        List<FarmRunPatch> stops=Arrays.asList(new FarmRunPatch("A","",PatchType.HERB),new FarmRunPatch("B","",PatchType.HERB));
        RunPlanner.Plan result=build(stops,99,stock,preferences);
        assertEquals(ranarr,result.steps.get(0).crop);
        assertEquals(snap,result.steps.get(1).crop);
    }
}
