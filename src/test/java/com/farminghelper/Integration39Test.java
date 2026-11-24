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
    @Test public void requirementsEncodeAllItemsAndMatchNativeTarget()
    {
        WorldPoint point=new WorldPoint(2848,3934,0);
        List<Map<Integer,Integer>> groups=NavigationBridge.requirementGroups(Map.of(952,1,5295,3));
        assertEquals(List.of(Map.of(952,1),Map.of(5295,3)),groups);
        Object target=Map.of("point",point,"requirements",groups);
        assertTrue(NavigationBridge.matchesTarget(target,List.of(target)));
        assertFalse(NavigationBridge.matchesTarget(target,List.of(point)));
        assertFalse(NavigationBridge.matchesTarget(point,List.of(point,new WorldPoint(1,2,0))));
    }
    @Test public void bankQueryFallsBackWhenNativeRequirementStatusUnavailable()
    {
        List<PluginMessage> sent=new ArrayList<>();
        NavigationBridge bridge=new NavigationBridge(sent::add);
        bridge.enabled(true);
        WorldPoint from=new WorldPoint(3100,3200,0),patch=new WorldPoint(2848,3934,0),bank=new WorldPoint(3092,3243,0);
        bridge.requestBank(from,patch,bank,Map.of(5295,1));
        Object id=sent.get(0).getData().get("id");
        bridge.message(new PluginMessage("shortestpath","result",Map.of("id",id,"reached",true)));
        assertTrue(sent.stream().anyMatch(e->"path".equals(e.getName())&&bank.equals(e.getData().get("target"))));
    }
    @Test public void bankQueryUsesNativeDetourWhenConfirmed()
    {
        List<PluginMessage> sent=new ArrayList<>();
        NavigationBridge bridge=new NavigationBridge(sent::add);
        bridge.enabled(true);
        WorldPoint from=new WorldPoint(3100,3200,0),patch=new WorldPoint(2848,3934,0),bank=new WorldPoint(3092,3243,0);
        bridge.requestBank(from,patch,bank,Map.of(5295,1));
        Object id=sent.get(0).getData().get("id");
        bridge.message(new PluginMessage("shortestpath","result",Map.of("id",id,"reached",true,"targetStatus","BANK","bankVisited",true)));
        assertTrue(sent.stream().anyMatch(e->"path".equals(e.getName())&&NavigationBridge.matchesTarget(
            Map.of("point",patch,"requirements",List.of(Map.of(5295,1))),e.getData().get("target"))));
    }
    @Test public void recognizesAdditionalVerifiedTransportObjects()
    {
        WorldPoint from=new WorldPoint(3100,3200,0),to=new WorldPoint(2848,3934,0);
        List<TransportHint> hints=TransportHint.read(List.of(Map.of("origin",from,"destination",to,"objectInfo","Enter portal 12345","displayInfo","Weiss")));
        assertEquals(1,hints.size());assertEquals(12345,hints.get(0).objectId);
        assertEquals("Enter portal — Weiss",hints.get(0).instruction());
    }
}
