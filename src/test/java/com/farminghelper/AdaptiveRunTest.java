package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdaptiveRunTest
{
    private static FarmRunPatch p(String location,PatchType type){return new FarmRunPatch(location,"",type);}

    @Test public void championsLandingPromotesOnlySelectedReadyMembers()
    {
        FarmRunPatch bush=p("Champions' Guild",PatchType.BUSH),hops=p("Lumbridge",PatchType.HOPS),
            distant=p("Catherby",PatchType.ALLOTMENT),unready=p("Falador",PatchType.ALLOTMENT);
        List<FarmRunPatch> pinned=new ArrayList<>(List.of(distant,hops,bush,unready));
        List<FarmRunPatch> ready=new ArrayList<>(List.of(distant,hops,bush));
        RunOrder.promoteNearby(pinned,ready,new WorldPoint(3182,3356,0));
        assertEquals(List.of(bush,hops,distant),ready);
        assertEquals(List.of(bush,hops,distant,unready),pinned);
        ready=new ArrayList<>(List.of(distant,hops));
        RunOrder.promoteNearby(pinned,ready,new WorldPoint(3182,3356,0));
        assertEquals(List.of(hops,distant),ready);
        assertFalse(ready.contains(bush));
    }

    @Test public void faladorLandingThenNearbyBelladonna()
    {
        FarmRunPatch farm=p("Falador",PatchType.ALLOTMENT),belladonna=p("Draynor Manor",PatchType.BELLADONNA),
            distant=p("Catherby",PatchType.ALLOTMENT);
        List<FarmRunPatch> pinned=new ArrayList<>(List.of(distant,belladonna,farm));
        List<FarmRunPatch> ready=new ArrayList<>(pinned);
        RunOrder.promoteNearby(pinned,ready,new WorldPoint(3052,3309,0));
        assertEquals(farm,ready.get(0));
        ready.remove(farm);
        RunOrder.promoteNearby(pinned,ready,new WorldPoint(3052,3309,0));
        assertEquals(belladonna,ready.get(0));
        ready=new ArrayList<>(List.of(distant));
        RunOrder.promoteNearby(pinned,ready,new WorldPoint(3052,3309,0));
        assertEquals(List.of(distant),ready);
    }

    @Test public void walkingDoesNotCountAsArrival()
    {
        assertFalse(RunOrder.arrival(null,new WorldPoint(3000,3000,0)));
        assertFalse(RunOrder.arrival(new WorldPoint(3000,3000,0),new WorldPoint(3002,3002,0)));
        assertTrue(RunOrder.arrival(new WorldPoint(3000,3000,0),new WorldPoint(3052,3309,0)));
    }

    @Test public void onlyKnownStoredUltraAndMissingCarriedCompostTriggerWithdrawal()
    {
        int ultra=ItemID.BUCKET_ULTRACOMPOST,bucket=ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED;
        assertTrue(CompostChoice.withdrawUltra(ultra,Map.of(),5));
        assertFalse(CompostChoice.withdrawUltra(ultra,Map.of(),0));
        assertFalse(CompostChoice.withdrawUltra(ultra,Map.of(ultra,1),5));
        assertFalse(CompostChoice.withdrawUltra(ultra,Map.of(bucket,1),5));
        assertFalse(CompostChoice.withdrawUltra(ItemID.BUCKET_SUPERCOMPOST,Map.of(),5));
        assertEquals(ultra,CompostChoice.localItem(bucket,Map.of(),5));
        assertEquals(bucket,CompostChoice.localItem(bucket,Map.of(bucket,1),5));
        assertEquals(bucket,CompostChoice.localItem(bucket,Map.of(),0));
    }
}
