package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static org.junit.Assert.*;

public class Guide16Test
{
    private static FarmRunPatch herb(String name){return new FarmRunPatch(name,"",PatchType.HERB);}
    @Test public void preferencesExhaustThenAutomaticFallback()
    {
        List<FarmRunPatch> patches=List.of(herb("Falador"),herb("Ardougne"),herb("Catherby"),herb("Weiss"));
        Crop ranarr=CropCatalog.findByName(PatchType.HERB,"Ranarr"), snap=CropCatalog.findByName(PatchType.HERB,"Snapdragon"),
            irit=CropCatalog.findByName(PatchType.HERB,"Irit");
        Map<Integer,Integer> stock=Map.of(ranarr.getItemId(),1,snap.getItemId(),1,irit.getItemId(),2);
        Map<FarmRunPatch,Crop> result=SeedChoices.allocate(patches,72,stock,t->"Ranarr",p->"Ranarr");
        assertEquals(List.of(ranarr,snap,irit,irit),new ArrayList<>(result.values()));
        assertEquals(snap,SeedChoices.allocate(List.of(patches.get(0)),72,Map.of(snap.getItemId(),1),t->"Ranarr",p->"Ranarr").get(patches.get(0)));
        assertEquals(ranarr,SeedChoices.allocate(List.of(patches.get(0)),72,Map.of(),t->"",p->"Ranarr").get(patches.get(0)));
    }
    @Test public void stockSnapshotRoundTripIgnoresInvalidEntries()
    {
        Map<Integer,Integer> stock=Map.of(1,3,2,20,5295,50);
        assertEquals(stock,StockSnapshot.decode(StockSnapshot.encode(stock)));
        assertEquals(Map.of(1,3),StockSnapshot.decode("1:3,broken,2:-1,0:3,2:x"));
        assertTrue(StockSnapshot.decode(null).isEmpty());
    }
    @Test public void explicitTransportLegsAndExitBeforeMainlandRoute()
    {
        WorldPoint land=new WorldPoint(3050,3300,0), hesInside=new WorldPoint(1240,10070,0), sea=new WorldPoint(3730,10271,0), coral=new WorldPoint(3288,8868,1);
        FarmRunPatch hes=new FarmRunPatch("Farming Guild","Hespori",PatchType.HESPORI), seaweed=new FarmRunPatch("Seaweed","North",PatchType.SEAWEED),
            cor=new FarmRunPatch("Great Conch","East",PatchType.CORAL), ang=new FarmRunPatch("Anglers' Retreat","",PatchType.HARDWOOD_TREE);
        assertEquals(SpecialRoutes.HESPORI_ENTRANCE,SpecialRoutes.leg(hes,land,false,false,false,null).point);
        assertEquals(RouteLeg.Interaction.HESPORI_EXIT,SpecialRoutes.leg(herb("Falador"),hesInside,false,false,true,null).interaction);
        assertNull(SpecialRoutes.leg(hes,hesInside,false,false,true,null).point);
        assertEquals(SpecialRoutes.SEAWEED_ISLAND,SpecialRoutes.leg(seaweed,land,false,false,false,null).point);
        assertNull(SpecialRoutes.leg(seaweed,sea,false,false,false,null).point);
        assertEquals(RouteLeg.Interaction.SURFACE,SpecialRoutes.leg(herb("Falador"),sea,false,false,false,null).interaction);
        assertEquals(SpecialRoutes.CORAL_ENTRANCE,SpecialRoutes.leg(cor,land,false,false,false,null).point);
        assertEquals(coral,SpecialRoutes.leg(herb("Falador"),coral,false,false,false,null).point);
        WorldPoint live=new WorldPoint(3296,8861,1);
        assertEquals(live,SpecialRoutes.leg(cor,coral,false,false,false,live).point);
        assertEquals(SpecialRoutes.CORSAIR_MOORING,SpecialRoutes.leg(ang,land,false,false,false,null).point);
        assertEquals(Waypoints.destination(ang),SpecialRoutes.leg(ang,land,false,true,false,null).point);
        RouteLeg sailing=SpecialRoutes.leg(ang,land,true,false,false,null);
        assertEquals(SpecialRoutes.ANGLER_MOORING,sailing.point);assertFalse(sailing.probe);
    }
    @Test public void modernRouteProbeAvoidsUnreachableTargetAndUsesNearbyReachableTile()
    {
        List<PluginMessage> events=new ArrayList<>();NavigationBridge bridge=new NavigationBridge(events::add);bridge.enabled(true);
        WorldPoint start=new WorldPoint(3000,3000,0), dest=new WorldPoint(3005,3005,0), near=new WorldPoint(3006,3005,0);
        bridge.begin();bridge.request(start,dest,true);
        PluginMessage query=events.stream().filter(e->e.getName().equals("query")).findFirst().get();
        bridge.message(new PluginMessage("shortestpath","result",Map.of("id",query.getData().get("id"),"reached",false,"closest",near,"path",List.of(start,near))));
        assertEquals(near,events.get(events.size()-1).getData().get("target"));
        bridge.stop();assertEquals("clear",events.get(events.size()-1).getName());
        events.clear();bridge.begin();bridge.request(start,dest,true);
        query=events.stream().filter(e->e.getName().equals("query")).findFirst().get();
        bridge.message(new PluginMessage("shortestpath","result",Map.of("id",query.getData().get("id"),"reached",false,"path",List.of())));
        assertFalse(events.stream().anyMatch(e->e.getName().equals("path")));assertTrue(bridge.status().contains("No verified route"));
    }
    @Test public void legacyBridgeFallbackAndNoResendAfterManualCancel()
    {
        List<PluginMessage> events=new ArrayList<>();NavigationBridge bridge=new NavigationBridge(events::add);bridge.enabled(true);
        WorldPoint start=new WorldPoint(3000,3000,0), dest=new WorldPoint(3005,3005,0);
        bridge.begin();bridge.request(start,dest,true);for(int i=0;i<5;i++)bridge.tick(start);
        assertEquals("path",events.get(events.size()-1).getName());bridge.stop();events.clear();
        bridge.begin();bridge.request(start,dest,false);bridge.tick(start);
        PluginMessage target=events.get(events.size()-1);assertEquals("getTarget",target.getName());
        bridge.message(new PluginMessage("shortestpath","currentTarget",Map.of("id",target.getData().get("id"),"set",false)));
        int count=events.size();bridge.request(start,dest,false);bridge.stop();assertEquals(count,events.size());
    }
}
