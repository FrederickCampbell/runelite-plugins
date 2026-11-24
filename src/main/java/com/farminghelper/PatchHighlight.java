package com.farminghelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Quest Helper ObjectStep/NpcStep-style highlights, restricted to known targets. */
final class PatchHighlight extends Overlay
{
    private final Client client;
    private final Provider<FarmingHelperPlugin> plugin;
    private final Set<GameObject> objects=new HashSet<>();
    @Inject PatchHighlight(Client client, Provider<FarmingHelperPlugin> plugin)
    {
        this.client=client;this.plugin=plugin;setPosition(OverlayPosition.DYNAMIC);
    }
    void spawned(GameObject o)
    {
        if(o==null)return;
        if(PatchObjects.known(o.getId())){objects.add(o);return;}
        ObjectComposition c=client.getObjectDefinition(o.getId());
        if(c!=null && c.getImpostorIds()!=null)
            for(int id:c.getImpostorIds()) if(PatchObjects.known(id)){objects.add(o);return;}
        // Exact Quest Helper base patch IDs may not appear in decoder comments.
        if(QuestTargets.known(o.getId())) objects.add(o);
    }
    void despawned(GameObject o){objects.remove(o);}
    void clear(){objects.clear();}
    @Override public Dimension render(Graphics2D graphics)
    {
        FarmingHelperPlugin helper=plugin.get();FarmRunPatch patch=helper.activePatch();
        if(patch==null || client.getLocalPlayer()==null)return null;
        StepEngine.Action action=helper.activeAction();
        WorldPoint point=Waypoints.destination(patch);
        if(point==null || client.getPlane()!=point.getPlane())return null;
        if(patch.getPatchType()==PatchType.HESPORI && action==StepEngine.Action.HARVEST)return null;
        if(action==StepEngine.Action.PAY_REMOVE || action==StepEngine.Action.PROTECT_TREE)
        {
            int gardener=helper.gardener(patch);
            if(gardener>0 && client.getTopLevelWorldView()!=null)
                for(NPC npc:client.getTopLevelWorldView().npcs())
                    if(npc.getId()==gardener && npc.getWorldLocation().distanceTo(point)<=12)
                    {draw(graphics,npc.getConvexHull());return null;}
            return null;
        }
        QuestTargets.Definition definition=QuestTargets.get(patch);
        if(definition!=null)
        {
            for(GameObject object:objects)
            {
                if(object.getWorldView()!=client.getLocalPlayer().getWorldView() || object.getWorldLocation().distanceTo(point)>2)continue;
                ObjectComposition composition=client.getObjectDefinition(object.getId());
                ObjectComposition impostor=composition==null || composition.getImpostorIds()==null?null:composition.getImpostor();
                if(object.getId()==definition.objectId || (impostor!=null && impostor.getId()==definition.objectId))
                {draw(graphics,object.getClickbox());return null;}
            }
            LocalPoint local=LocalPoint.fromWorld(client,point);
            if(local!=null)
            {
                Polygon tile=Perspective.getCanvasTilePoly(client,local);
                if(tile!=null)OverlayUtil.renderPolygon(graphics,tile,Color.CYAN);
            }
        }
        else if(helper.activeLive())
        {
            // Resolve only the live area's objects. Directional names are used only
            // when every physical patch in the pair is observed. Otherwise retain
            // the sidebar instruction without a potentially misleading outline.
            WorldPoint anchor=patch.getPatchType()==PatchType.HESPORI?client.getLocalPlayer().getWorldLocation():point;
            List<List<GameObject>> groups=new ArrayList<>();
            List<GameObject> candidates=new ArrayList<>();
            for(GameObject object:objects)
            {
                if(object.getWorldView()!=client.getLocalPlayer().getWorldView() || object.getWorldLocation().distanceTo(anchor)>18)continue;
                ObjectComposition c=client.getObjectDefinition(object.getId());
                ObjectComposition imp=c==null || c.getImpostorIds()==null?null:c.getImpostor();
                if(PatchObjects.matches(patch.getPatchType(),object.getId()) || imp!=null && PatchObjects.matches(patch.getPatchType(),imp.getId())) candidates.add(object);
            }
            List<WorldPoint> positions=new ArrayList<>();
            while(!candidates.isEmpty())
            {
                List<GameObject> group=new ArrayList<>();group.add(candidates.remove(candidates.size()-1));
                for(int i=0;i<group.size();i++)
                    for(int j=candidates.size()-1;j>=0;j--)
                        if(group.get(i).getWorldLocation().distanceTo(candidates.get(j).getWorldLocation())<=1)group.add(candidates.remove(j));
                int x=0,y=0;for(GameObject o:group){x+=o.getWorldLocation().getX();y+=o.getWorldLocation().getY();}
                positions.add(new WorldPoint(x/group.size(),y/group.size(),group.get(0).getWorldLocation().getPlane()));groups.add(group);
            }
            int selected=select(patch.getName(),positions,helper.patchCount(patch));
            if(selected>=0)for(GameObject object:groups.get(selected))draw(graphics,object.getClickbox());
        }
        return null;
    }
    /** Conservative compass-pair resolver adapted from the previously tested WorldGuide. */
    static int select(String patchName,List<WorldPoint> positions,int expectedCount)
    {
        if(expectedCount==1 && positions.size()==1)return 0;
        if(expectedCount!=2 || positions.size()!=2)return -1;
        String name=patchName.toLowerCase(Locale.ROOT);
        int x=name.contains("east")?1:name.contains("west")?-1:0;
        int y=name.contains("north")?1:name.contains("south")?-1:0;
        if(x==0 && y==0 || name.matches(".*[0-9].*"))return -1;
        WorldPoint a=positions.get(0),b=positions.get(1);
        if(a.getPlane()!=b.getPlane())return -1;
        int dx=(a.getX()-b.getX())*x,dy=(a.getY()-b.getY())*y;
        if(dx*dy<0 || dx+dy==0)return -1;
        return dx+dy>0?0:1;
    }
    private static void draw(Graphics2D graphics,Shape shape)
    {
        if(shape==null)return;
        graphics.setColor(new Color(0,255,255,35));graphics.fill(shape);
        graphics.setColor(Color.CYAN);graphics.draw(shape);
    }
}
