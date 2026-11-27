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
        assertEquals(List.of("Camelot tablet"),plan.routes().get("Catherby"));assertEquals(List.of(),plan.routes().get("Yanille"));
        assertEquals(List.of(1),plan.priority(Map.of(1,Set.of("Catherby")),false));
        plan.closeVisit();assertEquals(List.of("Camelot tablet"),plan.routes().get("Catherby"));
        plan.reset();assertFalse(plan.message(Map.of("id",first.getData().get("id"),"reached",true)));
    }
    @Test public void noObjectTransportIsMistakenForAnInventoryItemAndWithdrawalsFreeze()
    {
        assertEquals(List.of("Camelot tablet",RouteSupplies.FAIRY),BankRoutePlan.descriptions(List.of(Map.of("displayInfo","Camelot tablet","objectInfo",""),Map.of("displayInfo","Camelot tablet","objectInfo","Enter Portal 123"),Map.of("displayInfo","ALS","objectInfo","Configure Fairy ring 29495"))));
        var sent=new ArrayList<PluginMessage>();var nav=new NavigationBridge(sent::add);nav.enabled(true);
        var plan=new BankRoutePlan();plan.begin(new WorldPoint(1253,3741,0),List.of(new FarmRunPatch("Catherby","",PatchType.HERB)));
        var fallback=Map.of(2,Set.of("Catherby"));
        assertEquals(List.of(2),plan.priority(fallback,false));
        plan.tick(nav);plan.freeze();
        plan.message(Map.of("id",sent.get(0).getData().get("id"),"reached",true,"transports",List.of(Map.of("displayInfo","Camelot tablet"))));
        assertEquals(List.of(2),plan.priority(Map.of(1,Set.of("Catherby")),false));
        assertEquals(List.of(1),plan.priority(Map.of(1,Set.of("Catherby")),true));
        assertTrue(plan.routes().isEmpty());
        plan.refresh();assertEquals(List.of("Camelot tablet"),plan.routes().get("Catherby"));
        assertTrue(BankRoutePlan.bankMode("INVENTORY_AND_BANK"));assertFalse(BankRoutePlan.bankMode("UNLOCKED"));
    }
    @Test public void timeoutAndLegacySilenceRetainFallback()
    {
        var sent=new ArrayList<PluginMessage>();var nav=new NavigationBridge(sent::add);nav.enabled(true);
        var plan=new BankRoutePlan();plan.begin(new WorldPoint(1253,3741,0),List.of(new FarmRunPatch("Catherby","",PatchType.HERB)));
        for(int i=0;i<40;i++)plan.tick(nav);
        assertTrue(plan.finished());assertEquals(1,sent.size());
        assertEquals(List.of(2),plan.priority(Map.of(2,Set.of("Catherby")),false));
    }
}
