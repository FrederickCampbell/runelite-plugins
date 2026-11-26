package com.farminghelper;

import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PatchHighlightPerformanceTest
{
    private static GameObject object(WorldView view,int plane,int x,int y,int width,int height)
    {
        GameObject object=mock(GameObject.class);
        when(object.getId()).thenReturn(8210);
        when(object.getWorldView()).thenReturn(view);when(object.getPlane()).thenReturn(plane);
        when(object.getSceneMinLocation()).thenReturn(new Point(x,y));
        when(object.getSceneMaxLocation()).thenReturn(new Point(x+width-1,y+height-1));
        when(object.getWorldLocation()).thenReturn(new WorldPoint(2500+x,3000+y,plane));
        return object;
    }

    @Test public void footprintUsesAllTilesAndTheCorrectWorldViewAndPlane()
    {
        WorldView view=mock(WorldView.class),other=mock(WorldView.class);
        when(view.getId()).thenReturn(17);
        GameObject a=object(view,1,10,20,2,1),b=object(view,1,12,21,2,2);
        PatchHighlight.Footprint area=PatchHighlight.Footprint.of(List.of(a,b,
            object(view,0,40,40,1,1),object(other,1,60,60,1,1)),view,1);
        assertNotNull(area);assertSame(view,area.view);assertEquals(1,area.plane);
        assertEquals(10,area.minX);assertEquals(20,area.minY);
        assertEquals(4,area.width());assertEquals(3,area.height());
        assertEquals(12*Perspective.LOCAL_TILE_SIZE,area.center.getX());
        assertEquals(21*Perspective.LOCAL_TILE_SIZE+Perspective.LOCAL_TILE_SIZE/2,area.center.getY());
        assertEquals(17,area.center.getWorldView());
        assertNull(PatchHighlight.Footprint.of(List.of(),view,1));
        verify(a,never()).getClickbox();verify(b,never()).getClickbox();
    }

    @Test public void repeatedFramesProjectOneDensePatchWithoutModelClickboxes()
    {
        Client client=mock(Client.class);Player player=mock(Player.class);WorldView view=mock(WorldView.class);
        FarmingHelperPlugin helper=mock(FarmingHelperPlugin.class);
        FarmRunPatch patch=new FarmRunPatch("Yanille","",PatchType.HOPS);
        when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldView()).thenReturn(view);
        when(view.getId()).thenReturn(WorldView.TOPLEVEL);
        when(helper.activePatch()).thenReturn(patch);when(helper.activeAction()).thenReturn(StepEngine.Action.HARVEST);
        when(helper.activeLive()).thenReturn(true);when(helper.patchVarbit(patch)).thenReturn(123);
        when(helper.inPatchRegion(eq(patch),any(WorldPoint.class))).thenReturn(true);
        ObjectComposition definition=mock(ObjectComposition.class);
        when(client.getObjectDefinition(8210)).thenReturn(definition);when(definition.getVarbitId()).thenReturn(123);
        PatchHighlight overlay=new PatchHighlight(client,()->helper);
        List<GameObject> crops=new ArrayList<>();
        for(int x=10;x<16;x++)for(int y=20;y<24;y++)
        {
            GameObject crop=object(view,0,x,y,1,1);crops.add(crop);overlay.spawned(crop);
        }
        PatchHighlight.Footprint area=overlay.footprint(patch);
        assertNotNull(area);assertEquals(6,area.width());assertEquals(4,area.height());
        Graphics2D graphics=new BufferedImage(200,200,BufferedImage.TYPE_INT_ARGB).createGraphics();
        Polygon poly=new Polygon(new int[]{20,120,120,20},new int[]{20,20,100,100},4);
        try(MockedStatic<Perspective> projection=mockStatic(Perspective.class))
        {
            projection.when(()->Perspective.getCanvasTileAreaPoly(client,area.center,6,4,0,0)).thenReturn(poly);
            for(int frame=0;frame<5;frame++)overlay.render(graphics);
            projection.verify(()->Perspective.getCanvasTileAreaPoly(client,area.center,6,4,0,0),times(5));
            verify(helper,times(1)).currentTransport();
            for(GameObject crop:crops)
            {
                verify(crop,times(1)).getSceneMinLocation();
                verify(crop,never()).getClickbox();
            }
            assertSame(area,overlay.footprint(patch));
            when(client.getTickCount()).thenReturn(1);
            assertNotSame(area,overlay.footprint(patch));
            PatchHighlight.Footprint next=overlay.footprint(patch);
            overlay.despawned(crops.get(0));assertNotSame(next,overlay.footprint(patch));
            // The active view's plane, not a top-level client plane, controls scene identity.
            when(view.getPlane()).thenReturn(1);
            assertNull(overlay.footprint(patch));
            when(view.getPlane()).thenReturn(0);
            assertNotNull(overlay.footprint(patch));
            // Temporary loss of the player invalidates cached geometry without losing spawn events.
            when(client.getLocalPlayer()).thenReturn(null);
            assertNull(overlay.footprint(patch));
            when(client.getLocalPlayer()).thenReturn(player);
            assertNotNull(overlay.footprint(patch));
            WorldView other=mock(WorldView.class);
            when(player.getWorldView()).thenReturn(other);
            assertNull(overlay.footprint(patch));
            overlay.clear();assertNull(overlay.footprint(patch));
        }
        finally {graphics.dispose();}
    }

    @Test public void nativeCompostUsesOnlyTheVerifiedPhysicalPatchAndDoesNotLatch()
    {
        Client client=mock(Client.class);Player player=mock(Player.class);WorldView view=mock(WorldView.class);
        FarmingHelperPlugin helper=mock(FarmingHelperPlugin.class);
        FarmRunPatch patch=new FarmRunPatch("Varrock","",PatchType.TREE);
        when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldView()).thenReturn(view);
        when(view.getId()).thenReturn(WorldView.TOPLEVEL);
        // Match the exact Quest Helper location in the current scene.
        when(view.getBaseX()).thenReturn(3200);when(view.getBaseY()).thenReturn(3400);
        GameObject tree=object(view,0,29,59,3,3);
        when(tree.getId()).thenReturn(net.runelite.api.gameval.ObjectID.FARMING_TREE_PATCH_3);
        when(tree.getWorldLocation()).thenReturn(new WorldPoint(3229,3459,0));
        ObjectComposition definition=mock(ObjectComposition.class);
        when(client.getObjectDefinition(tree.getId())).thenReturn(definition);
        int varbit=net.runelite.api.gameval.VarbitID.FARMING_COMPOST_VARBIT_TREE_3_TRANSMIT;
        when(client.getVarbitValue(varbit)).thenReturn(3,0,3);
        PatchHighlight overlay=new PatchHighlight(client,()->helper);overlay.spawned(tree);
        assertEquals(3,overlay.liveCompostTier(patch));
        assertEquals(0,overlay.liveCompostTier(patch));
        assertEquals(3,overlay.liveCompostTier(patch));
        overlay.despawned(tree);
        assertEquals(-1,overlay.liveCompostTier(patch));
    }

    @Test public void transitionTargetIsResolvedOncePerTickAndClearedOnDespawn()
    {
        Client client=mock(Client.class);Player player=mock(Player.class);WorldView view=mock(WorldView.class);
        FarmingHelperPlugin helper=mock(FarmingHelperPlugin.class);
        when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldView()).thenReturn(view);
        WorldPoint point=new WorldPoint(2510,3020,0);
        when(helper.activeLeg()).thenReturn(RouteLeg.approach(point,"Bank",RouteLeg.Interaction.BIRD_BANK));
        GameObject bank=object(view,0,10,20,1,1);when(bank.getId()).thenReturn(SpecialRoutes.SEAWEED_BANK_ID);
        ObjectComposition definition=mock(ObjectComposition.class);
        when(client.getObjectDefinition(SpecialRoutes.SEAWEED_BANK_ID)).thenReturn(definition);
        PatchHighlight overlay=new PatchHighlight(client,()->helper);overlay.spawned(bank);
        clearInvocations(client,bank);
        Graphics2D graphics=new BufferedImage(100,100,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            for(int frame=0;frame<5;frame++)overlay.render(graphics);
            verify(client,times(1)).getObjectDefinition(SpecialRoutes.SEAWEED_BANK_ID);
            verify(bank,times(5)).getClickbox();
            overlay.despawned(bank);overlay.render(graphics);
            verify(bank,times(5)).getClickbox();
        }
        finally {graphics.dispose();}
    }

    @Test public void unloadedWorldViewDoesNotRetainSceneObjects()
    {
        Client client=mock(Client.class);FarmingHelperPlugin helper=mock(FarmingHelperPlugin.class);
        WorldView a=mock(WorldView.class),b=mock(WorldView.class);
        GameObject first=object(a,0,10,20,1,1),second=object(b,0,10,20,1,1);
        PatchHighlight overlay=new PatchHighlight(client,()->helper);
        overlay.spawned(first);overlay.spawned(second);overlay.removeView(a);
        List<GameObject> left=new ArrayList<>();overlay.objects().forEach(left::add);
        assertEquals(List.of(second),left);
    }
}
