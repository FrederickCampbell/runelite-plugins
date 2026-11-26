package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreparationTravel44Test
{
    @Test public void initialAndResupplyPreferKnownVaultPlantingShortage()
    {
        Map<Integer,Integer> needed=Map.of(1,5),bank=Map.of(1,2),vault=Map.of(1,3);
        WorldPoint from=new WorldPoint(3092,3243,0);
        for(boolean initial:new boolean[]{true,false})
        {
            assertSame(Preparation.SEED_VAULT,Preparation.chooseSupplies(70,from,initial,Preparation.StartAt.BANK,false,needed,Map.of(),bank,vault));
            assertNotSame(Preparation.SEED_VAULT,Preparation.chooseSupplies(40,from,initial,Preparation.StartAt.BANK,false,needed,Map.of(),bank,vault));
            assertNotSame(Preparation.SEED_VAULT,Preparation.chooseSupplies(70,from,initial,Preparation.StartAt.BANK,false,needed,Map.of(1,3),bank,vault));
        }
        assertSame(Preparation.FOSSIL_BANK,Preparation.chooseSupplies(70,from,false,Preparation.StartAt.BANK,true,needed,Map.of(),Map.of(1,5),vault));
        assertTrue(Preparation.sourceNote(needed,Map.of(),bank,vault,false,true).contains("outdated"));
    }

    @Test public void lateReadyStopsKeepTheCurrentItineraryAndTerminalLegs()
    {
        FarmRunPatch first=new FarmRunPatch("Catherby","",PatchType.HERB),next=new FarmRunPatch("Weiss","",PatchType.HERB);
        FarmRunPatch late=new FarmRunPatch("Morytania","",PatchType.HERB),end=BirdhouseRun.patches().get(0);
        List<FarmRunPatch> itinerary=new ArrayList<>(List.of(first,next,end));
        RunOrder.appendReady(itinerary,List.of(late,next,late));
        assertEquals(List.of(first,next,late,end),itinerary);
    }

    @Test public void teleportReferencesMatchTheActualGrowingSite()
    {
        FarmRunPatch hops=new FarmRunPatch("Lumbridge","",PatchType.HOPS);
        FarmRunPatch tree=new FarmRunPatch("Lumbridge","",PatchType.TREE);
        FarmRunPatch belladonna=new FarmRunPatch("Auburnvale","",PatchType.BELLADONNA);
        assertEquals(Map.of(1,Set.of("Lumbridge farm"),2,Set.of("Lumbridge Castle"),3,Set.of("Auburnvale belladonna")),
            RunTeleports.select(List.of(hops,tree,belladonna),Map.of(1,List.of("Chronicle"),2,List.of("Lumbridge tablet"),
                3,List.of("Trinket of fairies: Tool Lep - Nemus Retreat")),Map.of()));
    }

    @Test public void plantingBeforeTools()
    {
        int palm=CropCatalog.findByName(PatchType.FRUIT_TREE,"Palm sapling").getItemId();
        Map<Integer,Integer> req=new LinkedHashMap<>();req.put(1,1);req.put(2,1);req.put(palm,5);req.put(3,2);
        java.util.function.IntFunction<String> names=id->id==1?"Spade":id==2?"Coins":id==3?"Ultracompost":"Palm sapling";
        assertEquals(List.of(palm,3,2,1),SupplyOrder.ordered(req,names));
        assertEquals(Map.of(palm,5),SupplyOrder.plantingRequirements(req,names));
    }

    @Test public void recommendationsAreOwnedRelevantDeduplicatedAndPreferCarried()
    {
        List<FarmRunPatch> ready=List.of(new FarmRunPatch("Lletya","",PatchType.FRUIT_TREE),
            new FarmRunPatch("Prifddinas","",PatchType.CRYSTAL_TREE),
            new FarmRunPatch("Weiss","",PatchType.HERB),new FarmRunPatch("Falador","",PatchType.HERB));
        Map<Integer,List<String>> eligible=Map.of(1,List.of("Eternal teleport crystal: Lletya","Eternal teleport crystal: Prifddinas"),
            2,List.of("Teleport crystal: Lletya","Teleport crystal: Prifddinas"),3,List.of("Icy basalt"),4,List.of("Chronicle"));
        Map<Integer,Set<String>> selected=RunTeleports.select(ready,eligible,Map.of(2,1));
        assertEquals(Map.of(2,Set.of("Lletya","Prifddinas"),3,Set.of("Weiss")),selected);
        assertTrue(RunTeleports.select(List.of(),eligible,Map.of()).isEmpty());
    }
    @Test public void everyMainlandDestinationHasARealTransportAndAnglersRetreatUsesItsBoat() throws Exception
    {
        List<String> destinations=new ArrayList<>();
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(
            getClass().getResourceAsStream("/farming-teleport-shelf.tsv"),java.nio.charset.StandardCharsets.UTF_8)))
        {
            reader.lines().filter(line->!line.startsWith("#")).forEach(line->destinations.add(line.split("\\t",-1)[8]));
        }
        List<FarmRunPatch> patches=new ArrayList<>(FarmRunCatalog.patches());patches.addAll(BirdhouseRun.patches());
        for(FarmRunPatch patch:patches)
        {
            boolean boat="Anglers' Retreat".equals(RunOrder.site(patch));
            assertEquals(patch.key(),boat,RunTeleports.select(List.of(patch),Map.of(1,destinations),Map.of()).isEmpty());
        }
    }

    @Test public void sharedBankSelectionPreservesInitialAndResupplySemantics()
    {
        WorldPoint guild=new WorldPoint(1253,3741,1);
        assertEquals("Farming Guild",Preparation.choose(70,guild,true,false).name);
        assertEquals("Draynor Village",Preparation.choose(70,guild,true,true).name);
        assertEquals("Farming Guild",Preparation.choose(70,guild,false,false).name);
        assertNotEquals("Farming Guild",Preparation.choose(70,guild,true,false,Preparation.StartAt.BANK,false).name);
        assertEquals("Draynor Village",Preparation.choose(40,null,false,false).name);
    }
}
