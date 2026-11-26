package com.farminghelper;

import java.util.*;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class FixedRouteCompostTest
{
    private static FarmRunPatch p(String location,PatchType type){return new FarmRunPatch(location,"",type);}
    private static List<FarmRunPatch> order(FarmRunPatch... patches)
    {return RunOrder.flatten(RunOrder.shared(List.of(patches),null,true,Set.of()));}

    @Test public void mixedRouteKeepsRequestedPairings()
    {
        FarmRunPatch herb=p("Catherby",PatchType.HERB),fruit=p("Catherby",PatchType.FRUIT_TREE),
            allotment=p("Catherby",PatchType.ALLOTMENT),farm=p("Falador",PatchType.HERB),
            manor=p("Draynor Manor",PatchType.BELLADONNA),park=p("Falador",PatchType.TREE),
            taverley=p("Taverley",PatchType.TREE),champ=p("Champions' Guild",PatchType.BUSH),
            hops=p("Lumbridge",PatchType.HOPS);
        List<FarmRunPatch> ordered=order(fruit,herb,allotment,manor,taverley,park,farm,hops,champ);
        assertEquals(List.of(champ,hops,farm,manor,park,taverley,herb,allotment,fruit),ordered);
        assertEquals(List.of(park,taverley,fruit),order(taverley,fruit,park));
        assertEquals(List.of(farm,manor),order(manor,farm));
        assertEquals(List.of(herb,fruit),order(fruit,herb));
    }

    @Test public void leprechaunAndBucketReduceSupplyRequirements()
    {
        int ultra=ItemID.BUCKET_ULTRACOMPOST,bucket=ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED;
        Map<Integer,Integer> required=new HashMap<>();
        CompostChoice.addRequired(required,ultra,10,15,Map.of(),Map.of());
        assertTrue(required.isEmpty());
        CompostChoice.addRequired(required,ultra,10,7,Map.of(),Map.of());
        assertEquals(Map.of(ultra,3),required);
        required.clear();
        CompostChoice.addRequired(required,ultra,10,7,Map.of(bucket,1),Map.of());
        assertEquals(Map.of(bucket,1),required);
        required.clear();
        CompostChoice.addRequired(required,bucket,10,0,Map.of(bucket,1),Map.of(bucket,1));
        assertEquals(Map.of(bucket,1),required);
        assertEquals(bucket,CompostChoice.ULTRA.plan(Map.of(bucket,1),Map.of(bucket,1),15));
        assertEquals(ultra,CompostChoice.BOTTOMLESS.plan(Map.of(),Map.of(bucket,1),15));
        assertEquals(bucket,CompostChoice.ULTRA.plan(Map.of(),Map.of(bucket,1),0));
        assertEquals(ItemID.BUCKET_SUPERCOMPOST,CompostChoice.SUPER.plan(Map.of(bucket,1),Map.of(bucket,1),15));
        assertEquals(-1,CompostChoice.NONE.plan(Map.of(bucket,1),Map.of(bucket,1),15));
        assertTrue(CompostChoice.withdrawUltra(ultra,Map.of(),5));
        assertFalse(CompostChoice.withdrawUltra(ultra,Map.of(),0));
        assertFalse(CompostChoice.withdrawUltra(ultra,Map.of(ultra,1),5));
        assertFalse(CompostChoice.withdrawUltra(ultra,Map.of(bucket,1),5));
        assertEquals(bucket,CompostChoice.localItem(ultra,Map.of(bucket,1),5));
        assertEquals(ultra,CompostChoice.localItem(bucket,Map.of(),5));
    }
}
