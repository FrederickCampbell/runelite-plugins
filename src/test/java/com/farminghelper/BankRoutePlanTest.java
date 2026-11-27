package com.farminghelper;
import java.util.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static org.junit.Assert.*;
public class BankRoutePlanTest
{
    @Test public void bankQueriesAreSequentialAndNeverChangeDisplayedPath()
    {
        var sent=new ArrayList<PluginMessage>();var nav=new NavigationBridge(sent::add);nav.enabled(true);
        var plan=new BankRoutePlan();var from=new WorldPoint(1253,3741,0);
        plan.begin(from,List.of(new FarmRunPatch("Catherby","",PatchType.HERB),new FarmRunPatch("Catherby","",PatchType.FRUIT_TREE),new FarmRunPatch("Yanille","",PatchType.HOPS)));
        plan.tick(nav);plan.tick(nav);assertEquals(1,sent.size());
        var first=sent.get(0);assertEquals(from,first.getData().get("start"));
        assertFalse(plan.message(Map.of("id","old","reached",true)));
        assertTrue(plan.message(Map.of("id",first.getData().get("id"),"reached",true,"transports",List.of(Map.of("displayInfo","Camelot tablet","objectInfo","")))));
        plan.tick(nav);assertEquals(2,sent.size());
        assertTrue(plan.message(Map.of("id",sent.get(1).getData().get("id"),"reached",true,"transports",List.of())));
        assertTrue(plan.finished());assertTrue(sent.stream().allMatch(m->"query".equals(m.getName())));
        assertEquals(List.of(1),plan.priority(Map.of(1,List.of("Camelot tablet"),2,List.of("Camelot tablet")),Map.of(1,1,2,0),Map.of(),Map.of(2,Set.of("Yanille")),false));
        plan.reset();assertFalse(plan.message(Map.of("id",first.getData().get("id"),"reached",true)));
    }
    @Test public void noObjectTransportIsMistakenForAnInventoryItemAndWithdrawalsFreeze()
    {
        assertEquals(Set.of("Camelot tablet"),BankRoutePlan.descriptions(List.of(Map.of("displayInfo","Camelot tablet","objectInfo",""),Map.of("displayInfo","Camelot tablet","objectInfo","Enter Portal 123"))));
        var sent=new ArrayList<PluginMessage>();var nav=new NavigationBridge(sent::add);nav.enabled(true);
        var plan=new BankRoutePlan();plan.begin(new WorldPoint(1253,3741,0),List.of(new FarmRunPatch("Catherby","",PatchType.HERB)));
        var fallback=Map.of(2,Set.of("Catherby"));
        assertEquals(List.of(2),plan.priority(Map.of(),Map.of(),Map.of(),fallback,false));
        plan.tick(nav);plan.freeze();
        plan.message(Map.of("id",sent.get(0).getData().get("id"),"reached",true,"transports",List.of(Map.of("displayInfo","Camelot tablet"))));
        assertEquals(List.of(2),plan.priority(Map.of(1,List.of("Camelot tablet")),Map.of(1,1),Map.of(),fallback,false));
        assertEquals(List.of(1),plan.priority(Map.of(1,List.of("Camelot tablet")),Map.of(1,1),Map.of(),fallback,true));
        assertTrue(BankRoutePlan.bankMode("INVENTORY_AND_BANK"));assertFalse(BankRoutePlan.bankMode("UNLOCKED"));
    }
    @Test public void timeoutAndLegacySilenceRetainFallback()
    {
        var sent=new ArrayList<PluginMessage>();var nav=new NavigationBridge(sent::add);nav.enabled(true);
        var plan=new BankRoutePlan();plan.begin(new WorldPoint(1253,3741,0),List.of(new FarmRunPatch("Catherby","",PatchType.HERB)));
        for(int i=0;i<40;i++)plan.tick(nav);
        assertTrue(plan.finished());assertEquals(1,sent.size());
        assertEquals(List.of(2),plan.priority(Map.of(),Map.of(),Map.of(),Map.of(2,Set.of("Catherby")),false));
    }
}
