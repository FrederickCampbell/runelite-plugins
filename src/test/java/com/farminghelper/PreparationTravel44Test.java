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
}
