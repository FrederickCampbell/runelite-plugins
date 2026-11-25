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
    private Object shownTarget;
    private boolean enabled, owned, probe, refreshPending, modern;
    private int ticks, sequence;
    private String queryId, targetId, clearId, status="";
    private Object clearTarget;
    private List<TransportHint> hints=Collections.emptyList();
    TransportHint nearbyTransport(WorldPoint player){return enabled&&owned?TransportHint.nearby(hints,player):null;}
    NavigationBridge(Consumer<PluginMessage> send){this.send=send;}
    void enabled(boolean value){if(enabled!=value){reset(false);modern=false;enabled=value;}}
    String status(){return status;}
    void begin(){reset(true);}
    void stop(){reset(true);}
    private void reset(boolean clear)
    {
        if(clear)releaseDisplayed();
        else {clearId=null;clearTarget=null;}
        requested=null;displayed=null;start=null;owned=false;probe=false;refreshPending=false;
        shownTarget=null;
        queryId=null;targetId=null;status="";ticks=0;hints=Collections.emptyList();
    }
    void request(WorldPoint from,WorldPoint to,boolean verify)
    {
        if(!enabled)return;
        if(Objects.equals(requested,to)&&probe==verify)return;
        releaseDisplayed();
        requested=from==null?null:to;displayed=null;owned=false;start=from;probe=verify;ticks=0;status="";
        shownTarget=null;
        queryId=null;targetId=null;refreshPending=false;hints=Collections.emptyList();
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
    private void releaseDisplayed()
    {
        if(!enabled||!owned||displayed==null)return;
        if(!modern){post("clear",null);return;}
        // No exclusive ownership token exists in the public API. Recheck the
        // complete target immediately before clearing; a new display cancels
        // the pending release. Legacy clients retain their original behavior.
        clearId="farming-helper-clear-"+(++sequence);
        clearTarget=shownTarget;
        post("getTarget",Map.of("id",clearId));
    }
    private void display(WorldPoint point){display(point,point);}
    private void display(WorldPoint point,Object target)
    {
        if(start==null||point==null)return;
        clearId=null;clearTarget=null;
        displayed=point;shownTarget=target;owned=true;status="";
        post("path",Map.of("start",start,"target",target));
    }
    // Only one destination is owned; never clear a different or multi-target route.
    static boolean matchesTarget(Object expected,Object actual)
    {
        if(actual instanceof Collection)
        {
            Collection<?> targets=(Collection<?>)actual;
            return targets.size()==1&&matchesTarget(expected,targets.iterator().next());
        }
        return Objects.equals(expected,actual);
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
        if("currentTarget".equals(event.getName())&&clearId!=null&&clearId.equals(id))
        {
            Object expected=clearTarget;clearId=null;clearTarget=null;
            if(Boolean.TRUE.equals(data.get("set"))&&matchesTarget(expected,data.get("target")))post("clear",null);
            return;
        }
        if("result".equals(event.getName()) && queryId!=null && queryId.equals(id))
        {
            modern=true;queryId=null;targetId=null;
            hints=TransportHint.read(data.get("transports"));
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
            boolean same=Boolean.TRUE.equals(data.get("set"))&&matchesTarget(shownTarget,target);
            // Respect a user's cancellation or another helper replacing the route.
            if(!same){owned=false;hints=Collections.emptyList();return;}
            // One fresh calculation after the next game tick so Shortest Path has
            // had a chance to refresh its inventory/equipment transport facts.
            if(refresh)display(displayed,shownTarget);
        }
    }
    private void post(String action,Map<String,Object> data)
    {send.accept(data==null?new PluginMessage("shortestpath",action):new PluginMessage("shortestpath",action,data));}
}
