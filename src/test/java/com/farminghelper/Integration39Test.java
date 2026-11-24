package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static org.junit.Assert.*;

public class Integration39Test
{
    @Test public void houseTemplateIsNotAnUnknownInstance()
    {
        assertTrue(LocationContext.isHouse(new WorldPoint(1858,7051,0)));
        assertFalse(LocationContext.isHouse(new WorldPoint(1800,7051,0)));
        assertFalse(LocationContext.isHouse(new WorldPoint(1253,3741,0)));
        assertFalse(LocationContext.isHouse(null));
    }
    @Test public void navigationDoesNotRequestBankDetoursEvenIfResultContainsBankMetadata()
    {
        List<PluginMessage> sent=new ArrayList<>();
        NavigationBridge bridge=new NavigationBridge(sent::add);bridge.enabled(true);
        WorldPoint from=new WorldPoint(3100,3200,0),patch=new WorldPoint(2848,3934,0);
        bridge.request(from,patch,true);
        PluginMessage query=sent.get(0);
        assertEquals(patch,query.getData().get("target"));
        bridge.message(new PluginMessage("shortestpath","result",Map.of("id",query.getData().get("id"),"reached",true,"targetStatus","BANK","bankVisited",true)));
        assertTrue(sent.stream().anyMatch(e->"path".equals(e.getName())&&patch.equals(e.getData().get("target"))));
        assertTrue(sent.stream().filter(e->"path".equals(e.getName())||"query".equals(e.getName())).allMatch(e->patch.equals(e.getData().get("target"))));
        assertTrue(NavigationBridge.matchesTarget(patch,List.of(patch)));
        assertFalse(NavigationBridge.matchesTarget(patch,List.of(patch,from)));
    }
    @Test public void modernClearChecksCurrentOwnership()
    {
        List<PluginMessage> sent=new ArrayList<>();
        NavigationBridge bridge=new NavigationBridge(sent::add);bridge.enabled(true);
        WorldPoint start=new WorldPoint(3100,3200,0),target=new WorldPoint(2848,3934,0),other=new WorldPoint(3000,3000,0);
        bridge.request(start,target,true);
        Object query=sent.get(0).getData().get("id");
        bridge.message(new PluginMessage("shortestpath","result",Map.of("id",query,"reached",true)));
        sent.clear();bridge.stop();
        assertEquals("getTarget",sent.get(0).getName());
        Object check=sent.get(0).getData().get("id");
        bridge.message(new PluginMessage("shortestpath","currentTarget",Map.of("id",check,"set",true,"target",List.of(other))));
        assertFalse(sent.stream().anyMatch(e->"clear".equals(e.getName())));
        bridge.request(start,target,true);
        PluginMessage pending=sent.stream().filter(e->"query".equals(e.getName())).findFirst().get();
        bridge.message(new PluginMessage("shortestpath","result",Map.of("id",pending.getData().get("id"),"reached",true)));
        sent.clear();bridge.stop();check=sent.get(0).getData().get("id");
        bridge.message(new PluginMessage("shortestpath","currentTarget",Map.of("id",check,"set",true,"target",List.of(target))));
        assertTrue(sent.stream().anyMatch(e->"clear".equals(e.getName())));
    }
    @Test public void recognizesAdditionalVerifiedTransportObjects()
    {
        WorldPoint from=new WorldPoint(3100,3200,0),to=new WorldPoint(2848,3934,0);
        List<TransportHint> hints=TransportHint.read(List.of(Map.of("origin",from,"destination",to,"objectInfo","Enter portal 12345","displayInfo","Weiss")));
        assertEquals(1,hints.size());assertEquals(12345,hints.get(0).objectId);
        assertEquals("Enter portal — Weiss",hints.get(0).instruction());
    }
}
