package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Quest Helper object targets and Advisor-style grouping, with region + varbit identity.
 * The anchor is the actual object, not the tile on which to stand. */
final class PatchHighlight extends Overlay
{
    private final Client client;
    private final Provider<FarmingHelperPlugin> plugin;
    private final Set<GameObject> objects=new HashSet<>();
    private final Map<FarmRunPatch,List<GameObject>> resolved=new HashMap<>();
    private int resolvedTick=-1,resolvedPlane=-1;
    private WorldView resolvedView;
    @Inject PatchHighlight(Client client,Provider<FarmingHelperPlugin> plugin)
    {this.client=client;this.plugin=plugin;setPosition(OverlayPosition.DYNAMIC);setLayer(net.runelite.client.ui.overlay.OverlayLayer.ABOVE_WIDGETS);}
    Iterable<GameObject> objects(){return objects;}
    void spawned(GameObject object)
    {
        if(object==null)return;
        ObjectComposition c=client.getObjectDefinition(object.getId());
        if(object.getId()==30796 || object.getId()==30919 || BirdhouseRun.isObject(object.getId()) || PatchObjects.known(object.getId()) || QuestTargets.known(object.getId()) || isTransition(c) || isVine(c) || verificationObject(c))
        {if(objects.add(object))resolved.clear();return;}
        if(c!=null && c.getImpostorIds()!=null)
            for(int id:c.getImpostorIds()) if(PatchObjects.known(id) || QuestTargets.known(id) || verificationObject(client.getObjectDefinition(id))){if(objects.add(object))resolved.clear();return;}
    }
    void despawned(GameObject o){if(objects.remove(o))resolved.clear();}
    void clear(){objects.clear();resolved.clear();resolvedView=null;}
    void transformed(){resolved.clear();}
    private static String name(ObjectComposition c){return c==null || c.getName()==null?"":c.getName().toLowerCase(Locale.ROOT);}
    private static boolean isBin(ObjectComposition c){return name(c).contains("compost bin");}
    private static boolean verificationObject(ObjectComposition c)
    {return c!=null&&(isBin(c)||QuestFarmingStateReader.matchesLiveName(c.getName(),true));}
    private static boolean isVine(ObjectComposition c){String n=name(c);return n.contains("grape") || n.contains("vine patch");}
    private static boolean hasAction(ObjectComposition c,String action)
    {if(c==null || c.getActions()==null)return false;for(String a:c.getActions())if(a!=null && a.equalsIgnoreCase(action))return true;return false;}
    private static boolean isTransition(ObjectComposition c)
    {
        String n=name(c);
        return n.contains("cave entrance") || n.contains("cave exit") || n.equals("steps") || n.contains("rowboat")
            || n.contains("mooring") || n.contains("gangplank") || n.contains("mushtree") || hasAction(c,"Surface") || hasAction(c,"Dive");
    }
    private ObjectComposition transformed(ObjectComposition c)
    {return c==null || c.getImpostorIds()==null?c:c.getImpostor();}
    private boolean sameView(GameObject o)
    {return client.getLocalPlayer()!=null && o.getWorldView()==client.getLocalPlayer().getWorldView() && o.getPlane()==client.getPlane();}
    private WorldPoint template(GameObject o)
    {return LocationContext.normalize(client,o.getLocalLocation(),o.getWorldLocation());}
    boolean hasVerifiedDisease(FarmRunPatch patch)
    {
        int varbit=plugin.get().patchVarbit(patch);
        if(varbit<=0)return false;
        for(GameObject object:resolve(patch))
        {
            ObjectComposition base=client.getObjectDefinition(object.getId()), actual=transformed(base);
            if(base==null||base.getVarbitId()!=varbit||actual==null||actual.getActions()==null)continue;
            for(String action:actual.getActions())if("Cure".equalsIgnoreCase(action)||"Prune".equalsIgnoreCase(action))return true;
        }
        return false;
    }

    boolean hasLocalPatch(FarmRunPatch patch)
    {
        return !resolve(patch).isEmpty();
    }

    private List<GameObject> resolve(FarmRunPatch patch)
    {
        if(patch==null || client.getLocalPlayer()==null)return Collections.emptyList();
        WorldView view=client.getLocalPlayer().getWorldView();
        int tick=client.getTickCount(),plane=client.getPlane();
        if(tick!=resolvedTick||view!=resolvedView||plane!=resolvedPlane)
        {resolved.clear();resolvedTick=tick;resolvedView=view;resolvedPlane=plane;}
        return resolved.computeIfAbsent(patch,this::match);
    }

    private List<GameObject> match(FarmRunPatch patch)
    {
        if(patch.getPatchType()==PatchType.BIRDHOUSE)
        {
            BirdhouseRun.Site site=BirdhouseRun.site(patch);if(site==null)return Collections.emptyList();
            List<GameObject> matched=new ArrayList<>();for(GameObject o:objects)if(o.getId()==site.object&&sameView(o))matched.add(o);
            return matched;
        }
        FarmingHelperPlugin helper=plugin.get();
        WorldPoint marker=Waypoints.destination(patch);
        QuestTargets.Definition exact=QuestTargets.get(patch);
        int varbit=helper.patchVarbit(patch);
        List<GameObject> verified=new ArrayList<>(),candidates=new ArrayList<>();
        for(GameObject o:objects)
        {
            if(!sameView(o))continue;
            WorldPoint point=template(o);if(point==null)continue;
            ObjectComposition base=client.getObjectDefinition(o.getId()), imp=transformed(base);
            boolean exactId=exact!=null && (o.getId()==exact.objectId || imp!=null && imp.getId()==exact.objectId);
            if(exactId && point.distanceTo(exact.point)<=3){verified.add(o);continue;}
            if(!helper.inPatchRegion(patch,point))continue;
            boolean type=PatchObjects.matches(patch.getPatchType(),o.getId()) || imp!=null && PatchObjects.matches(patch.getPatchType(),imp.getId())
                || patch.getPatchType()==PatchType.GRAPEVINE && (isVine(base)||isVine(imp))
                || patch.getPatchType()==PatchType.COMPOST_BIN && (isBin(base)||isBin(imp));
            if(!type)continue;
            if(patch.getPatchType()==PatchType.COMPOST_BIN && (base==null||base.getVarbitId()!=varbit))continue;
            // The base object's transform varbit can directly bind a numbered or compass patch.
            if(varbit>0 && base!=null && base.getVarbitId()==varbit){verified.add(o);continue;}
            if(patch.getPatchType()==PatchType.HESPORI || patch.getPatchType()==PatchType.CORAL || marker!=null && point.distanceTo2D(marker)<=25)candidates.add(o);
        }
        if(!verified.isEmpty())return verified;
        if(exact!=null)return Collections.emptyList();
        // Advisor's connected-object technique, applied only to verified farming regions.
        List<List<GameObject>> groups=new ArrayList<>();List<WorldPoint> positions=new ArrayList<>();
        while(!candidates.isEmpty())
        {
            List<GameObject> group=new ArrayList<>();group.add(candidates.remove(candidates.size()-1));
            for(int i=0;i<group.size();i++)for(int j=candidates.size()-1;j>=0;j--)
                if(group.get(i).getWorldLocation().distanceTo(candidates.get(j).getWorldLocation())<=1)group.add(candidates.remove(j));
            int x=0,y=0;for(GameObject o:group){x+=o.getWorldLocation().getX();y+=o.getWorldLocation().getY();}
            positions.add(new WorldPoint(x/group.size(),y/group.size(),group.get(0).getPlane()));groups.add(group);
        }
        int chosen=select(patch.getName(),positions,helper.patchCount(patch));
        return chosen<0?Collections.emptyList():groups.get(chosen);
    }
    /** Find a nearby walkable perimeter tile. Do not return a guessed tile if collision data is absent. */
    WorldPoint approach(FarmRunPatch patch)
    {
        List<GameObject> group=resolve(patch);
        if(group.isEmpty() || client.getLocalPlayer()==null)return null;
        WorldView view=group.get(0).getWorldView();
        if(view==null || view.isInstance() || !view.isTopLevel())return null;
        CollisionData[] maps=view.getCollisionMaps();int plane=group.get(0).getPlane();
        if(maps==null || plane<0 || plane>=maps.length || maps[plane]==null)return null;
        int[][] flags=maps[plane].getFlags();if(flags==null)return null;
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE;
        for(GameObject o:group)
        {WorldPoint p=o.getWorldLocation();int x=p.getX()-(o.sizeX()-1)/2,y=p.getY()-(o.sizeY()-1)/2;
            minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x+Math.max(1,o.sizeX())-1);maxY=Math.max(maxY,y+Math.max(1,o.sizeY())-1);}
        WorldPoint reference=Waypoints.destination(patch),best=null;int score=Integer.MAX_VALUE;
        if(reference==null || reference.getPlane()!=plane)reference=group.get(0).getWorldLocation();
        for(int x=minX-1;x<=maxX+1;x++)for(int y=minY-1;y<=maxY+1;y++)
        {
            if(x>=minX && x<=maxX && y>=minY && y<=maxY)continue;
            // Cardinal perimeter tiles are proper interaction approaches.
            if((x<minX || x>maxX) && (y<minY || y>maxY))continue;
            int sx=x-view.getBaseX(),sy=y-view.getBaseY();
            if(sx<0 || sx>=flags.length || sy<0 || sy>=flags[sx].length || (flags[sx][sy]&CollisionDataFlag.BLOCK_MOVEMENT_FULL)!=0)continue;
            WorldPoint p=new WorldPoint(x,y,plane);int d=reference.distanceTo(p);
            if(d<score){best=p;score=d;}
        }
        return best;
    }
    private boolean transitionMatches(RouteLeg.Interaction interaction,GameObject object)
    {
        ObjectComposition base=client.getObjectDefinition(object.getId()),c=transformed(base);if(c==null)c=base;
        String n=name(c);int id=object.getId();
        switch(interaction)
        {
            case HESPORI_ENTER:return n.contains("cave entrance");
            case HESPORI_EXIT:return n.contains("cave exit") || n.contains("cave entrance") && hasAction(c,"Exit");
            case BIRD_BANK:return id==30796;
            case BIRD_ROW:return id==30919;
            case DIVE:return id==30919 || n.contains("rowboat") && hasAction(c,"Dive");
            case SURFACE:return hasAction(c,"Surface") || hasAction(c,"Climb-up") && n.contains("rope");
            case CORAL_ENTER:return id==57904 || c!=null && c.getId()==57904;
            case CORAL_EXIT:return id==57903 || c!=null && c.getId()==57903;
            case BOARD:return n.contains("gangplank") && (hasAction(c,"Board") || hasAction(c,"Cross"));
            case MOOR:return n.contains("mooring") || n.contains("gangplank");
            default:return false;
        }
    }
    /** Resolve a local NPC from the scene, never a guessed map target. */
    NPC leprechaun(FarmRunPatch patch)
    {
        if(client.getLocalPlayer()==null||client.getTopLevelWorldView()==null)return null;
        WorldPoint anchor=Waypoints.destination(patch),player=client.getLocalPlayer().getWorldLocation();
        if(anchor==null)return null;
        NPC best=null;int distance=Integer.MAX_VALUE;
        for(NPC npc:client.getTopLevelWorldView().npcs())
        {
            if(!"Tool leprechaun".equalsIgnoreCase(npc.getName()))continue;
            WorldPoint point=npc.getWorldLocation();
            if(point==null||point.distanceTo(anchor)>25||point.distanceTo(player)>25)continue;
            int d=point.distanceTo(player);
            if(d<distance){best=npc;distance=d;}
        }
        return best;
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        FarmingHelperPlugin helper=plugin.get();if(client.getLocalPlayer()==null)return null;
        RouteLeg leg=helper.activeLeg();
        if(leg!=null && leg.interaction!=RouteLeg.Interaction.NONE)
        {
            if(leg.interaction==RouteLeg.Interaction.BIRD_ROW)
            {
                var options=client.getWidget(219,1);
                if(options!=null&&!options.isHidden()&&options.getDynamicChildren()!=null)
                    for(var choice:options.getDynamicChildren())
                        if(choice!=null&&!choice.isHidden()&&"Row to the camp.".equals(choice.getText()))
                        {draw(graphics,choice.getBounds());return null;}
            }
            GameObject selected=null;int distance=Integer.MAX_VALUE;
            WorldPoint anchor=leg.point==null?client.getLocalPlayer().getWorldLocation():leg.point;
            for(GameObject o:objects)if(sameView(o) && transitionMatches(leg.interaction,o))
            {int d=o.getWorldLocation().distanceTo(anchor);if(d<distance && d<=18){selected=o;distance=d;}}
            if(selected!=null)draw(graphics,selected.getClickbox());return null;
        }
        TransportHint transport=helper.currentTransport();
        if(transport!=null)
        {
            GameObject best=null;int distance=Integer.MAX_VALUE;
            for(GameObject o:objects)if(sameView(o))
            {
                ObjectComposition actual=transformed(client.getObjectDefinition(o.getId()));
                if(o.getId()!=transport.objectId&&(actual==null||actual.getId()!=transport.objectId))continue;
                int d=o.getWorldLocation().distanceTo(transport.origin);
                if(d<distance&&d<=6){distance=d;best=o;}
            }
            if(best!=null){draw(graphics,best.getClickbox());return null;}
        }
        FarmRunPatch patch=helper.activePatch();if(patch==null)return null;
        StepEngine.Action action=helper.activeAction();
        if(helper.fetchingUltra())
        {
            NPC npc=leprechaun(patch);
            if(npc!=null)draw(graphics,npc.getConvexHull());
            return null;
        }
        if(patch.getPatchType()==PatchType.HESPORI && action==StepEngine.Action.HARVEST)return null;
        if(action==StepEngine.Action.PAY_REMOVE || action==StepEngine.Action.PROTECT_TREE)
        {
            int gardener=helper.gardener(patch);
            if(gardener>0 && helper.activeLive() && client.getTopLevelWorldView()!=null)
                for(NPC npc:client.getTopLevelWorldView().npcs())if(npc.getId()==gardener && npc.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation())<=25)
                {draw(graphics,npc.getConvexHull());return null;}
            return null;
        }
        List<GameObject> group=resolve(patch);
        for(GameObject o:group)draw(graphics,o.getClickbox());
        if(group.isEmpty() && QuestTargets.get(patch)!=null)
        {
            WorldPoint point=QuestTargets.get(patch).point;LocalPoint local=LocalPoint.fromWorld(client,point);
            if(local!=null){Polygon tile=Perspective.getCanvasTilePoly(client,local);if(tile!=null)OverlayUtil.renderPolygon(graphics,tile,Color.CYAN);}
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
