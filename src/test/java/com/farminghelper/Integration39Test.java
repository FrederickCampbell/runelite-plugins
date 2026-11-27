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
    @Test public void teleportInvalidatesPendingRouteFromOldOrigin()
    {
        List<PluginMessage> sent=new ArrayList<>();
        NavigationBridge bridge=new NavigationBridge(sent::add);bridge.enabled(true);
        WorldPoint start=new WorldPoint(3100,3200,0),als=new WorldPoint(2644,3495,0),patch=new WorldPoint(2661,3523,0);
        bridge.request(start,patch,true);
        Object oldId=sent.get(0).getData().get("id");
        bridge.request(als,patch,true);
        PluginMessage latest=sent.stream().filter(e->"query".equals(e.getName())).reduce((a,b)->b).get();
        assertEquals(als,latest.getData().get("start"));
        assertFalse(bridge.message(new PluginMessage("shortestpath","result",Map.of("id",oldId,"reached",true))));
        assertTrue(bridge.message(new PluginMessage("shortestpath","result",Map.of("id",latest.getData().get("id"),"reached",true))));
        assertTrue(sent.stream().anyMatch(e->"path".equals(e.getName())&&als.equals(e.getData().get("start"))));
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

    @Test public void legacySilenceNeverClearsAndLateOwnershipReplyExpires()
    {
        List<PluginMessage> sent=new ArrayList<>();
        NavigationBridge bridge=new NavigationBridge(sent::add);bridge.enabled(true);
        WorldPoint start=new WorldPoint(3100,3200,0),target=new WorldPoint(2848,3934,0);
        bridge.request(start,target,false);bridge.stop();
        PluginMessage check=sent.get(sent.size()-1);
        assertEquals("getTarget",check.getName());
        for(int i=0;i<5;i++)bridge.tick(start);
        assertFalse(bridge.message(new PluginMessage("shortestpath","currentTarget",Map.of("id",check.getData().get("id"),"set",true,"target",target))));
        assertFalse(sent.stream().anyMatch(e->"clear".equals(e.getName())));
    }
    @Test public void identicalDestinationWithExternalConfigIsNotOwnedAndIdsNeverRepeat()
    {
        List<PluginMessage> sent=new ArrayList<>();
        WorldPoint start=new WorldPoint(3100,3200,0),target=new WorldPoint(2848,3934,0);
        NavigationBridge first=new NavigationBridge(sent::add);first.enabled(true);first.request(start,target,false);first.stop();
        Object oldId=sent.get(sent.size()-1).getData().get("id");
        NavigationBridge second=new NavigationBridge(sent::add);second.enabled(true);second.request(start,target,false);second.stop();
        Object newId=sent.get(sent.size()-1).getData().get("id");
        assertNotEquals(oldId,newId);
        assertFalse(second.message(new PluginMessage("shortestpath","currentTarget",Map.of("id",oldId,"set",true,"target",target))));
        assertTrue(second.message(new PluginMessage("shortestpath","currentTarget",Map.of("id",newId,"set",true,"target",List.of(target),"config",Map.of("external",true)))));
        assertFalse(sent.stream().anyMatch(e->"clear".equals(e.getName())));
        assertFalse(NavigationBridge.matchesTarget(target,List.of(List.of(target))));
    }
    @Test public void aNewDisplayInvalidatesPendingRelease()
    {
        List<PluginMessage> sent=new ArrayList<>();
        NavigationBridge bridge=new NavigationBridge(sent::add);bridge.enabled(true);
        WorldPoint start=new WorldPoint(3100,3200,0),a=new WorldPoint(2848,3934,0),b=new WorldPoint(3000,3000,0);
        bridge.request(start,a,false);bridge.stop();
        Object id=sent.get(sent.size()-1).getData().get("id");
        bridge.request(start,b,false);
        assertFalse(bridge.message(new PluginMessage("shortestpath","currentTarget",Map.of("id",id,"set",true,"target",a))));
        assertFalse(sent.stream().anyMatch(e->"clear".equals(e.getName())));
    }
}
