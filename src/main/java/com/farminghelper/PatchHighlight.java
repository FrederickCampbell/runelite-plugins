package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.runelite.api.gameval.InterfaceID;
import javax.inject.Inject;
import javax.inject.Provider;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Quest Helper object targets and Advisor-style grouping, with region + varbit identity.
 * The anchor is the actual object, not the tile on which to stand. */
final class PatchHighlight extends Overlay
{
    private static final Pattern ROW_NUMBER = Pattern.compile("^\\s*(?:\\[\\d+\\]|\\d+[.)])\\s*");
    private static final Color CYAN_FILL = new Color(0,255,255,35), GREEN_FILL = new Color(0,255,0,35);
    private static final Stroke OUTLINE = new BasicStroke(2f);
    private static final Stroke CROSS = new BasicStroke(2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
    private static final Stroke CROSS_SHADOW = new BasicStroke(3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
    private static final Polygon ARROW = new Polygon(new int[]{-7,7,7,17,0,-17,-7},new int[]{-44,-44,-20,-20,0,-20,-20},7);

    private final Client client;
    private final Provider<FarmingHelperPlugin> plugin;
    private final Set<GameObject> objects=new HashSet<>();
    private final Map<Integer,Boolean> classified=new HashMap<>();
    private final Map<FarmRunPatch,List<GameObject>> resolved=new HashMap<>();
    private int resolvedTick=-1,resolvedPlane=-1;
    private WorldView resolvedView;
    private final Map<FarmRunPatch,Footprint> footprints=new HashMap<>();
    private final Map<FarmRunPatch,NPC> leprechauns=new HashMap<>(),gardeners=new HashMap<>();
    private RouteLeg.Interaction transitionKind;
    private WorldPoint transitionAnchor;
    private GameObject transitionTarget,transportTarget;
    private FarmRunPatch transportPatch;
    private RouteLeg transportLeg;
    private boolean transportResolved;
    // Keep scene outlines below the interface; highlight native exchange and dialogue widgets above it.
    private final Overlay dialogue=new Overlay()
    {
        @Override public Dimension render(Graphics2D graphics)
        {
            FarmingHelperPlugin helper=plugin.get();
            LeprechaunWithdrawal withdrawal=helper.leprechaunWithdrawal();
            if(withdrawal!=null)
            {
                boolean shown=false;
                for(LeprechaunWithdrawal plan=withdrawal;plan!=null;plan=plan.companion)
                {
                    Widget slot=exchangeSlot(plan);
                    if(slot==null)continue;
                    Rectangle bounds=slot.getBounds();
                    draw(graphics,bounds,Color.GREEN);
                    label(graphics,plan.label(),bounds.x+bounds.width/2,bounds.y+bounds.height-4,Color.GREEN);
                    shown=true;
                }
                if(shown)return null;
            }
            RouteLeg leg=helper.activeLeg();
            if(leg!=null&&leg.interaction==RouteLeg.Interaction.BIRD_ROW)
            {
                Widget choice=rowChoice();
                if(choice!=null)draw(graphics,choice.getBounds());
            }
            return null;
        }
    };
    @Inject PatchHighlight(Client client,Provider<FarmingHelperPlugin> plugin)
    {
        this.client=client;this.plugin=plugin;setPosition(OverlayPosition.DYNAMIC);
        dialogue.setPosition(OverlayPosition.DYNAMIC);dialogue.setLayer(OverlayLayer.ABOVE_WIDGETS);
    }
    Overlay dialogue(){return dialogue;}
    private Widget exchangeSlot(LeprechaunWithdrawal withdrawal)
    {
        Widget root=client.getWidget(InterfaceID.FarmingTools.UNIVERSE);
        if(root==null||root.isHidden())return null;
        Widget slot=client.getWidget(InterfaceID.FARMING_TOOLS,withdrawal.child);
        return slot==null||slot.isHidden()?null:slot;
    }
    static boolean matchesRowChoice(String text)
    {
        if(text==null)return false;
        String choice=ROW_NUMBER.matcher(Text.removeTags(text)).replaceFirst("").trim();
        if (choice.endsWith(".") || choice.endsWith("!")) choice=choice.substring(0,choice.length()-1).trim();
        return "Row to the camp".equalsIgnoreCase(choice)||"Row to the museum camp".equalsIgnoreCase(choice);
    }
    private Widget rowChoice()
    {
        Widget options=client.getWidget(InterfaceID.Chatmenu.OPTIONS);
        if(options==null||options.isHidden())return null;
        return findRowChoice(options);
    }
    private static Widget findRowChoice(Widget widget)
    {
        if(widget==null||widget.isHidden())return null;
        if(matchesRowChoice(widget.getText()))return widget;
        Widget choice = findRowChoice(widget.getChildren());
        return choice != null ? choice : findRowChoice(widget.getStaticChildren());
    }
    private static Widget findRowChoice(Widget[] children)
    {
        if (children != null) for (Widget child : children)
        {
            Widget found = findRowChoice(child);
            if (found != null) return found;
        }
        return null;
    }
    Iterable<GameObject> objects(){return objects;}
    void spawned(GameObject object)
    {
        if(object==null)return;
        int id=object.getId();
        if(knownObject(id)||Boolean.TRUE.equals(classified.computeIfAbsent(id,this::classify)))
            if(objects.add(object))invalidateSceneTargets();
    }
    private static boolean knownObject(int id)
    {
        return id==SpecialRoutes.SEAWEED_BANK_ID||id==SpecialRoutes.SEAWEED_BOAT_ID||id==SpecialRoutes.SEAWEED_ROPE_ID||
            BirdhouseRun.isObject(id)||PatchObjects.known(id)||QuestTargets.known(id);
    }
    private Boolean classify(int id)
    {
        ObjectComposition c=client.getObjectDefinition(id);
        if(c==null)return null;
        if(isTransition(c)||isVine(c)||verificationObject(c))return true;
        if(c.getImpostorIds()!=null)
            for(int variant:c.getImpostorIds())if(knownObject(variant)||verificationObject(client.getObjectDefinition(variant)))return true;
        return false;
    }
    void despawned(GameObject o){if(objects.remove(o))invalidateSceneTargets();}
    void removeView(WorldView view)
    {
        if(view==null)return;
        if(objects.removeIf(o->o.getWorldView()==view)||resolvedView==view)invalidateSceneTargets();
        if(resolvedView==view){resolvedView=null;resolvedTick=-1;resolvedPlane=-1;}
    }
    void clear(){objects.clear();classified.clear();invalidateSceneTargets();resolvedView=null;resolvedTick=-1;resolvedPlane=-1;}
    void transformed(){invalidateSceneTargets();}
    void npcChanged(){leprechauns.clear();gardeners.clear();}
    void transportChanged(){transportResolved=false;transportPatch=null;transportLeg=null;transportTarget=null;}
    private void invalidateSceneTargets()
    {
        resolved.clear();footprints.clear();npcChanged();transportChanged();
        transitionKind=null;transitionAnchor=null;transitionTarget=null;
    }
    private boolean refreshScene()
    {
        Player local=client.getLocalPlayer();
        WorldView view=local==null?null:local.getWorldView();
        if(view==null)
        {
            if(resolvedView!=null){invalidateSceneTargets();resolvedView=null;resolvedTick=-1;resolvedPlane=-1;}
            return false;
        }
        int tick=client.getTickCount(),plane=view.getPlane();
        if(tick!=resolvedTick||view!=resolvedView||plane!=resolvedPlane)
        {
            invalidateSceneTargets();resolvedTick=tick;resolvedView=view;resolvedPlane=plane;
        }
        return true;
    }
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
    {return resolvedView!=null && o.getWorldView()==resolvedView && o.getPlane()==resolvedPlane;}
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

    /** Only on a player item-on-object action: bind the clicked scene object to a
     * verified local patch, including runs that are not currently guided. */
    FarmRunPatch clickedPatch(List<FarmRunPatch> patches,int objectId,int x,int y)
    {
        for(FarmRunPatch patch:patches)
        {
            if(patch.getPatchType().isTree()||patch.getPatchType()==PatchType.BIRDHOUSE||patch.getPatchType()==PatchType.COMPOST_BIN)continue;
            for(GameObject object:resolve(patch))
            {
                var min=object.getSceneMinLocation();var max=object.getSceneMaxLocation();
                var actual=transformed(client.getObjectDefinition(object.getId()));
                if((object.getId()==objectId||actual!=null&&actual.getId()==objectId)&&min!=null&&max!=null&&
                    x>=min.getX()&&x<=max.getX()&&y>=min.getY()&&y<=max.getY())return patch;
            }
        }
        return null;
    }
    /** Reuse the verified local object group; never infer a compost varbit from
     * a crop model, reused growth varbit or an unverified nearby patch. */
    int liveCompostTier(FarmRunPatch patch)
    {
        int found=-1, growth=plugin.get().patchVarbit(patch);
        QuestTargets.Definition exact=QuestTargets.get(patch);
        for(GameObject object:resolve(patch))
        {
            int id=object.getId(), compost=CompostChoice.liveVarbit(id);
            if(compost<0)continue;
            ObjectComposition base=client.getObjectDefinition(id);
            if(!(exact!=null&&exact.objectId==id)&&(growth<=0||base==null||base.getVarbitId()!=growth))continue;
            if(found>=0&&found!=compost)return -1;
            found=compost;
        }
        return CompostChoice.liveTier(client,found);
    }
    private List<GameObject> resolve(FarmRunPatch patch)
    {
        if(patch==null || !refreshScene())return Collections.emptyList();
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
    /** One scene-space rectangle per verified patch. No model or clickbox geometry. */
    static final class Footprint
    {
        final WorldView view;
        final int plane,minX,minY,maxX,maxY;
        final LocalPoint center;
        Footprint(WorldView view,int plane,int minX,int minY,int maxX,int maxY)
        {
            this.view=view;this.plane=plane;this.minX=minX;this.minY=minY;this.maxX=maxX;this.maxY=maxY;
            center=new LocalPoint((minX+maxX+1)*Perspective.LOCAL_TILE_SIZE/2,
                (minY+maxY+1)*Perspective.LOCAL_TILE_SIZE/2,view);
        }
        int width(){return maxX-minX+1;}
        int height(){return maxY-minY+1;}
        Polygon project(Client client)
        {return Perspective.getCanvasTileAreaPoly(client,center,width(),height(),plane,0);}
        static Footprint of(List<GameObject> group,WorldView view,int plane)
        {
            if(view==null)return null;
            int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE;
            for(GameObject o:group)
            {
                if(o.getWorldView()!=view||o.getPlane()!=plane)continue;
                net.runelite.api.Point min=o.getSceneMinLocation(),max=o.getSceneMaxLocation();
                if(min==null||max==null||min.getX()>max.getX()||min.getY()>max.getY())continue;
                minX=Math.min(minX,min.getX());minY=Math.min(minY,min.getY());
                maxX=Math.max(maxX,max.getX());maxY=Math.max(maxY,max.getY());
            }
            return minX==Integer.MAX_VALUE?null:new Footprint(view,plane,minX,minY,maxX,maxY);
        }
    }
    Footprint footprint(FarmRunPatch patch)
    {
        if(patch==null||!refreshScene())return null;
        if(!footprints.containsKey(patch))footprints.put(patch,Footprint.of(resolve(patch),resolvedView,resolvedPlane));
        return footprints.get(patch);
    }
    /** Reuse the same verified footprint for a nearby walkable perimeter tile. */
    WorldPoint approach(FarmRunPatch patch)
    {
        Footprint area=footprint(patch);
        if(area==null||area.view.isInstance()||!area.view.isTopLevel())return null;
        WorldView view=area.view;int plane=area.plane;
        CollisionData[] maps=view.getCollisionMaps();
        if(maps==null||plane<0||plane>=maps.length||maps[plane]==null)return null;
        int[][] flags=maps[plane].getFlags();if(flags==null)return null;
        int baseX=view.getBaseX(), baseY=view.getBaseY();
        WorldPoint reference=Waypoints.destination(patch);
        boolean useMarker=reference!=null && reference.getPlane()==plane;
        int targetX=useMarker?reference.getX()-baseX:area.center.getSceneX();
        int targetY=useMarker?reference.getY()-baseY:area.center.getSceneY();
        int bestX=0, bestY=0, score=Integer.MAX_VALUE;
        for(int x=area.minX-1;x<=area.maxX+1;x++)for(int y=area.minY-1;y<=area.maxY+1;y++)
        {
            if(x>=area.minX&&x<=area.maxX&&y>=area.minY&&y<=area.maxY)continue;
            if((x<area.minX||x>area.maxX)&&(y<area.minY||y>area.maxY))continue;
            if(x<0||x>=flags.length||y<0||y>=flags[x].length||(flags[x][y]&CollisionDataFlag.BLOCK_MOVEMENT_FULL)!=0)continue;
            int d=Math.max(Math.abs(targetX-x),Math.abs(targetY-y));
            if(d<score){bestX=x;bestY=y;score=d;}
        }
        return score==Integer.MAX_VALUE?null:new WorldPoint(baseX+bestX,baseY+bestY,plane);
    }
    private GameObject nearest(WorldPoint anchor,int radius,Predicate<GameObject> match)
    {
        if(anchor==null)return null;
        GameObject best=null;int distance=Integer.MAX_VALUE;
        for(GameObject o:objects)if(sameView(o))
        {
            WorldPoint point=o.getWorldLocation();if(point==null)continue;
            int d=point.distanceTo(anchor);
            if(d<distance&&d<=radius&&match.test(o)){best=o;distance=d;}
        }
        return best;
    }
    private GameObject transition(RouteLeg leg)
    {
        WorldPoint anchor=leg.point==null?client.getLocalPlayer().getWorldLocation():leg.point;
        if(transitionKind!=leg.interaction||!Objects.equals(transitionAnchor,anchor))
        {
            transitionKind=leg.interaction;transitionAnchor=anchor;
            transitionTarget=nearest(anchor,18,o->transitionMatches(leg.interaction,o));
        }
        return transitionTarget;
    }
    private GameObject transport(FarmingHelperPlugin helper,FarmRunPatch patch,RouteLeg leg)
    {
        if(!transportResolved||transportPatch!=patch||transportLeg!=leg)
        {
            transportResolved=true;transportPatch=patch;transportLeg=leg;
            TransportHint hint=helper.currentTransport();
            transportTarget=hint==null?null:nearest(hint.origin,6,o->{
                if(o.getId()==hint.objectId)return true;
                ObjectComposition actual=transformed(client.getObjectDefinition(o.getId()));
                return actual!=null&&actual.getId()==hint.objectId;
            });
        }
        return transportTarget;
    }
    private boolean transitionMatches(RouteLeg.Interaction interaction,GameObject object)
    {
        ObjectComposition base=client.getObjectDefinition(object.getId()),c=transformed(base);if(c==null)c=base;
        String n=name(c);int id=object.getId();
        switch(interaction)
        {
            case HESPORI_ENTER:return n.contains("cave entrance");
            case HESPORI_EXIT:return n.contains("cave exit") || n.contains("cave entrance") && hasAction(c,"Exit");
            case BIRD_BANK:return id==SpecialRoutes.SEAWEED_BANK_ID;
            case BIRD_ROW:return id==SpecialRoutes.SEAWEED_BOAT_ID;
            case DIVE:return id==30919 || n.contains("rowboat") && hasAction(c,"Dive");
            case SURFACE:return id==SpecialRoutes.SEAWEED_ROPE_ID;
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
        if(patch==null||!refreshScene())return null;
        if(!leprechauns.containsKey(patch))leprechauns.put(patch,findLeprechaun(patch));
        return leprechauns.get(patch);
    }
    private NPC findLeprechaun(FarmRunPatch patch)
    {
        Player local=client.getLocalPlayer();
        if(local==null||local.getWorldView()==null)return null;
        WorldPoint anchor=Waypoints.destination(patch),player=local.getWorldLocation();
        if(anchor==null)return null;
        WorldPoint normalizedPlayer=LocationContext.normalize(client,local.getLocalLocation(),player);
        NPC best=null;int distance=Integer.MAX_VALUE;
        for(NPC npc:local.getWorldView().npcs())
        {
            if(!"Tool leprechaun".equalsIgnoreCase(npc.getName()))continue;
            WorldPoint point=npc.getWorldLocation();
            WorldPoint normalizedNpc=LocationContext.normalize(client,npc.getLocalLocation(),point);
            if(!nearLeprechaun(normalizedNpc,anchor,normalizedPlayer)||point==null||player==null||
                point.getPlane()!=player.getPlane()||point.distanceTo(player)>25)continue;
            int d=point.distanceTo(player);
            if(d<distance){best=npc;distance=d;}
        }
        return best;
    }
    // Area markers may use plane 0, including the underwater seaweed and coral markers.
    // The verified local patch and same-plane player/NPC keep this a local interaction.
    static boolean nearLeprechaun(WorldPoint point,WorldPoint anchor,WorldPoint player)
    {
        return point!=null&&anchor!=null&&player!=null&&point.getPlane()==player.getPlane()&&
            point.distanceTo2D(anchor)<=25&&point.distanceTo(player)<=25;
    }
    private NPC gardener(FarmRunPatch patch,FarmingHelperPlugin helper)
    {
        if(!gardeners.containsKey(patch))
        {
            int id=helper.gardener(patch);NPC best=null;int distance=Integer.MAX_VALUE;
            WorldPoint player=client.getLocalPlayer().getWorldLocation();
            if(id>0&&player!=null)for(NPC npc:resolvedView.npcs())
            {
                if(npc.getId()!=id||npc.getWorldLocation()==null)continue;
                int d=npc.getWorldLocation().distanceTo(player);
                if(d<=25&&d<distance){best=npc;distance=d;}
            }
            gardeners.put(patch,best);
        }
        return gardeners.get(patch);
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        if(!refreshScene())return null;
        FarmingHelperPlugin helper=plugin.get();
        RouteLeg leg=helper.activeLeg();
        if(leg!=null&&leg.interaction!=RouteLeg.Interaction.NONE)
        {
            if(leg.interaction==RouteLeg.Interaction.BIRD_ROW&&rowChoice()!=null)return null;
            GameObject selected=transition(leg);
            if(selected!=null)
            {
                Shape shape=selected.getClickbox();
                if(leg.interaction==RouteLeg.Interaction.BIRD_BANK)
                {
                    draw(graphics,shape,Color.GREEN);
                    if(shape!=null)
                    {
                        Rectangle b=shape.getBounds();
                        arrow(graphics,b.x+b.width/2,b.y-4);
                    }
                }
                else draw(graphics,shape);
            }
            return null;
        }
        FarmRunPatch patch=helper.activePatch();if(patch==null)return null;
        GameObject travel=transport(helper,patch,leg);
        if(travel!=null){draw(graphics,travel.getClickbox());return null;}
        StepEngine.Action action=helper.activeAction();
        LeprechaunWithdrawal withdrawal=helper.leprechaunWithdrawal();
        if(withdrawal!=null&&exchangeSlot(withdrawal)==null)
        {
            NPC npc=leprechaun(patch);
            if(npc!=null)
            {
                draw(graphics,npc.getConvexHull(),Color.GREEN);
                net.runelite.api.Point point=npc.getCanvasTextLocation(graphics," ",npc.getLogicalHeight()+20);
                if(point!=null)arrow(graphics,point.getX(),point.getY());
            }
        }
        PatchType type=patch.getPatchType();
        if(type==PatchType.HESPORI&&action==StepEngine.Action.HARVEST)return null;
        if(action==StepEngine.Action.PAY_REMOVE||action==StepEngine.Action.PROTECT_TREE)
        {
            NPC npc=helper.activeLive()?gardener(patch,helper):null;
            if(npc!=null)draw(graphics,npc.getConvexHull());
            return null;
        }
        // Single interaction objects retain their native clickbox, never a loop of crop models.
        if(type==PatchType.BIRDHOUSE||type==PatchType.COMPOST_BIN)
        {
            List<GameObject> group=resolve(patch);
            if(!group.isEmpty())draw(graphics,group.get(0).getClickbox());
            return null;
        }
        Footprint area=footprint(patch);
        Polygon polygon=area==null?null:area.project(client);
        if(area==null&&resolve(patch).isEmpty()&&QuestTargets.get(patch)!=null&&!resolvedView.isInstance())
        {
            WorldPoint point=QuestTargets.get(patch).point;
            LocalPoint local=LocalPoint.fromWorld(resolvedView,point);
            if(local!=null)polygon=Perspective.getCanvasTileAreaPoly(client,local,1,1,resolvedPlane,0);
        }
        draw(graphics,polygon);
        if(area!=null&&polygon!=null&&helper.activeLive())
        {
            Rectangle bounds=polygon.getBounds();
            int x=bounds.x+bounds.width/2,y=bounds.y-6;
            String actionLabel=StepEngine.label(action,type);
            if(action==StepEngine.Action.COMPOST&&helper.compostMissing())careReminder(graphics,"Compost",x,y);
            else if(action==StepEngine.Action.WATER)careReminder(graphics,"Water",x,y);
            else
            {
                if(!actionLabel.isEmpty())label(graphics,actionLabel,x,y,Color.CYAN);
                if(helper.compostMissing())careReminder(graphics,"Compost",x,y-16);
            }
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
    private static void draw(Graphics2D graphics,Shape shape){draw(graphics,shape,Color.CYAN);}
    private static void draw(Graphics2D graphics,Shape shape,Color color)
    {
        if(shape==null)return;
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            g.setColor(Color.GREEN.equals(color)?GREEN_FILL:CYAN_FILL);g.fill(shape);
            g.setColor(color);if(Color.GREEN.equals(color))g.setStroke(OUTLINE);g.draw(shape);
        }
        finally{g.dispose();}
    }
    private static void arrow(Graphics2D graphics,int x,int y)
    {
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            g.translate(x,y);
            g.setColor(Color.GREEN);g.fill(ARROW);
            g.setColor(Color.BLACK);g.setStroke(OUTLINE);g.draw(ARROW);
        }
        finally{g.dispose();}
    }
    // Draw the cross rather than relying on platform-dependent emoji fonts.
    private static void careReminder(Graphics2D graphics,String text,int center,int baseline)
    {
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            g.setFont(g.getFont().deriveFont(Font.BOLD,13f));
            int width=g.getFontMetrics().stringWidth(text);
            int x=center-(width+15)/2;
            g.setColor(Color.BLACK);g.drawString(text,x+1,baseline+1);
            g.setColor(Color.CYAN);g.drawString(text,x,baseline);
            int cross=x+width+5;
            g.setStroke(CROSS_SHADOW);
            g.setColor(Color.BLACK);g.drawLine(cross+1,baseline-9,cross+9,baseline-1);g.drawLine(cross+9,baseline-9,cross+1,baseline-1);
            g.setStroke(CROSS);
            g.setColor(Color.RED);g.drawLine(cross,baseline-10,cross+8,baseline-2);g.drawLine(cross+8,baseline-10,cross,baseline-2);
        }
        finally{g.dispose();}
    }
    private static void label(Graphics2D graphics,String text,int center,int baseline,Color color)
    {
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            g.setFont(g.getFont().deriveFont(Font.BOLD,13f));
            int x=center-g.getFontMetrics().stringWidth(text)/2;
            g.setColor(Color.BLACK);g.drawString(text,x+1,baseline+1);
            g.setColor(color);g.drawString(text,x,baseline);
        }
        finally{g.dispose();}
    }
}
