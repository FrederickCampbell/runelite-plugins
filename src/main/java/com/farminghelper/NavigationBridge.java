package com.farminghelper;

import java.util.*;
import java.util.function.Consumer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;

/** Optional Shortest Path message bridge. A modern query is a reachability check,
 * never a second pathfinder. Older versions fall back after a bounded wait. */
final class NavigationBridge
{
    private final Consumer<PluginMessage> send;
    private WorldPoint requested, displayed, start;
    private boolean enabled, owned, probe, refreshPending, modern;
    private int ticks, sequence;
    private String queryId, targetId, status="";
    NavigationBridge(Consumer<PluginMessage> send){this.send=send;}
    void enabled(boolean value){if(enabled!=value){reset(false);modern=false;enabled=value;}}
    String status(){return status;}
    // Only use background ordering queries after the installed API has responded
    // and the active navigation request is no longer awaiting its own probe.
    boolean canProbeOrder(){return enabled&&modern&&queryId==null&&!refreshPending;}
    void begin(){reset(true);if(enabled)post("clear",null);}
    void stop(){reset(true);}
    private void reset(boolean clear)
    {
        if(clear && enabled && owned && displayed!=null)post("clear",null);
        requested=null;displayed=null;start=null;owned=false;probe=false;refreshPending=false;
        queryId=null;targetId=null;status="";ticks=0;
    }
    void request(WorldPoint from,WorldPoint to,boolean verify)
    {
        if(!enabled)return;
        if(Objects.equals(requested,to) && probe==verify)return;
        if(owned && displayed!=null)post("clear",null);
        requested=from==null?null:to;displayed=null;owned=false;start=from;probe=verify;ticks=0;status="";
        queryId=null;targetId=null;refreshPending=false;
        if(to==null || from==null)return;
        if(verify)
        {
            queryId="farming-helper-query-"+(++sequence);
            status="Checking route…";
            post("query",Map.of("id",queryId,"start",from,"target",to));
            targetId="farming-helper-probe-"+(++sequence);
            post("getTarget",Map.of("id",targetId));
        }
        else {display(to);refreshPending=true;}
    }
    private void display(WorldPoint point)
    {
        if(start==null || point==null)return;
        displayed=point;owned=true;status="";
        post("path",Map.of("start",start,"target",point));
    }
    void tick(WorldPoint from)
    {
        if(!enabled || requested==null)return;
        if(from!=null)start=from;
        ticks++;
        if(queryId!=null && ticks>=(modern?30:5))
        {
            // No response: installed version may predate query. One legacy request only.
            queryId=null;
            if(modern)status="Route check did not finish. Follow the guide’s transport instruction.";
            else display(requested);
            return;
        }
        if(refreshPending && ticks>=1)
        {
            refreshPending=false;
            targetId="farming-helper-target-"+(++sequence);
            post("getTarget",Map.of("id",targetId));
        }
        else if(modern && owned && displayed!=null && ticks%10==0)
        {
            targetId="farming-helper-owner-"+(++sequence);
            post("getTarget",Map.of("id",targetId));
        }
    }
    void message(PluginMessage event)
    {
        if(!enabled || !"shortestpath".equals(event.getNamespace()) || event.getData()==null)return;
        Map<String,Object> data=event.getData();Object id=data.get("id");
        if("result".equals(event.getName()) && queryId!=null && queryId.equals(id))
        {
            modern=true;queryId=null;targetId=null;
            if(Boolean.TRUE.equals(data.get("reached"))){display(requested);return;}
            // A small reachable approach is preferable to a blocked object tile.
            Object near=data.get("closest");
            if(near instanceof WorldPoint && ((WorldPoint)near).getPlane()==requested.getPlane()
                && ((WorldPoint)near).distanceTo(requested)<=6 && data.get("path") instanceof Collection
                && !((Collection<?>)data.get("path")).isEmpty())
            {display((WorldPoint)near);status="Route ends at a nearby approach; follow the highlighted interaction.";return;}
            status="No verified route for this leg. Follow the guide's entrance or transport instruction.";
        }
        else if("currentTarget".equals(event.getName()) && targetId!=null && targetId.equals(id))
        {
            modern=true;boolean refresh=targetId.startsWith("farming-helper-target-");targetId=null;
            if(queryId!=null)return;
            Object target=data.get("target");
            boolean same=Boolean.TRUE.equals(data.get("set")) && (Objects.equals(displayed,target)
                || target instanceof Collection && ((Collection<?>)target).contains(displayed));
            // Respect a user's cancellation or another helper replacing the route.
            if(!same){owned=false;return;}
            // One fresh calculation after the next game tick so Shortest Path has
            // had a chance to refresh its inventory/equipment transport facts.
            if(refresh)display(displayed);
        }
    }
    private void post(String action,Map<String,Object> data)
    {send.accept(data==null?new PluginMessage("shortestpath",action):new PluginMessage("shortestpath",action,data));}
}
