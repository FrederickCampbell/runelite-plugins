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
        assertEquals(List.of("Camelot tablet"),plan.routes().get("Catherby"));assertFalse(plan.finished());
        plan.tick(nav);assertEquals(2,sent.size());
        assertEquals(RunOrder.surface(new FarmRunPatch("Catherby","",PatchType.FRUIT_TREE)),sent.get(1).getData().get("start"));
        assertTrue(plan.message(Map.of("id",sent.get(1).getData().get("id"),"reached",true,"transports",List.of())));
        assertTrue(plan.finished());assertTrue(sent.stream().allMatch(m->"query".equals(m.getName())));
        assertEquals(List.of("Camelot tablet"),plan.routes().get("Catherby"));assertEquals(List.of(),plan.routes().get("Yanille"));
        assertEquals(List.of(1),plan.priority(Map.of(1,Set.of("Catherby")),false));
        plan.closeVisit();assertEquals(List.of("Camelot tablet"),plan.routes().get("Catherby"));
        plan.reset();assertFalse(plan.message(Map.of("id",first.getData().get("id"),"reached",true)));
    }
    @Test public void noObjectTransportIsMistakenForAnInventoryItemAndResultsPromoteDependencies()
    {
        assertEquals(List.of("Camelot tablet",RouteSupplies.FAIRY),BankRoutePlan.descriptions(List.of(Map.of("displayInfo","Camelot tablet","objectInfo",""),Map.of("displayInfo","Camelot tablet","objectInfo","Enter Portal 123"),Map.of("displayInfo","ALS","objectInfo","Configure Fairy ring 29495"))));
        var sent=new ArrayList<PluginMessage>();var nav=new NavigationBridge(sent::add);nav.enabled(true);
        var plan=new BankRoutePlan();plan.begin(new WorldPoint(1253,3741,0),List.of(new FarmRunPatch("Catherby","",PatchType.HERB)));
        var fallback=Map.of(2,Set.of("Catherby"));
        assertEquals(List.of(2),plan.priority(fallback,false));
        plan.tick(nav);
        plan.message(Map.of("id",sent.get(0).getData().get("id"),"reached",true,"transports",List.of(Map.of("displayInfo","Camelot tablet"))));
        assertEquals(List.of(1),plan.priority(Map.of(1,Set.of("Catherby")),false));
        assertEquals(List.of(1),plan.priority(Map.of(1,Set.of("Catherby")),true));
        assertEquals(List.of("Camelot tablet"),plan.routes().get("Catherby"));
        assertTrue(BankRoutePlan.bankMode("INVENTORY_AND_BANK"));assertFalse(BankRoutePlan.bankMode("UNLOCKED"));
    }
    @Test public void cancelledBankQueryRetriesOnceWithoutChangingTheDisplayedPath()
    {
        var sent=new ArrayList<PluginMessage>();var nav=new NavigationBridge(sent::add);nav.enabled(true);
        var plan=new BankRoutePlan();plan.begin(new WorldPoint(1253,3741,0),List.of(new FarmRunPatch("Catherby","",PatchType.HERB)));
        plan.tick(nav);plan.message(Map.of("id",sent.get(0).getData().get("id"),"reason","CANCELLED"));
        plan.tick(nav);assertEquals(2,sent.size());assertEquals(sent.get(0).getData().get("target"),sent.get(1).getData().get("target"));
        plan.message(Map.of("id",sent.get(1).getData().get("id"),"reached",true,"transports",List.of(Map.of("displayInfo","Camelot Teleport"))));
        assertEquals(List.of("Camelot Teleport"),plan.routes().get("Catherby"));
        assertTrue(plan.finished());
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
