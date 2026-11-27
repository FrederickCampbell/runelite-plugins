package com.farminghelper;

import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.PluginMessage;

/** Optional Shortest Path message bridge. A modern query is a reachability check,
 * never a second pathfinder. Older versions fall back after a bounded wait. */
final class NavigationBridge
{
    private final Consumer<PluginMessage> send;
    private WorldPoint requested, displayed, start;
    private Set<WorldPoint> bankTargets=Set.of();
    private boolean enabled, owned, probe, refreshPending, modern;
    private int ticks,clearAge;
    private static final AtomicLong SEQUENCE=new AtomicLong();
    private String queryId, targetId, clearId, status="";
    private WorldPoint clearTarget;
    private List<TransportHint> hints=Collections.emptyList();
    TransportHint nearbyTransport(WorldPoint player){return enabled&&owned?TransportHint.nearby(hints,player):null;}
    NavigationBridge(Consumer<PluginMessage> send){this.send=send;}
    void enabled(boolean value){if(enabled!=value){reset(false);modern=false;enabled=value;}}
    boolean enabled(){return enabled;}
    String status(){return status;}
    void begin(){reset(true);}
    void stop(){reset(true);}
    private void reset(boolean clear)
    {
        if(clear)releaseDisplayed();
        else {clearId=null;clearTarget=null;}
        requested=null;displayed=null;start=null;bankTargets=Set.of();owned=false;probe=false;refreshPending=false;
        queryId=null;targetId=null;status="";ticks=0;hints=Collections.emptyList();
    }
    void request(WorldPoint from,WorldPoint to,boolean verify)
    {request(from,to,verify,Set.of());}
    void requestBanks(WorldPoint from,WorldPoint fallback,Set<WorldPoint> candidates)
    {request(from,fallback,true,candidates);}
    private void request(WorldPoint from,WorldPoint to,boolean verify,Set<WorldPoint> candidates)
    {
        if(!enabled)return;
        // In-flight queries from the pre-teleport origin must not supply hints
        // or an approach for the post-teleport scene.
        boolean jumped=from!=null&&start!=null&&(from.getPlane()!=start.getPlane()||from.distanceTo2D(start)>12);
        if(Objects.equals(requested,to)&&probe==verify&&bankTargets.equals(candidates)&&!(queryId!=null&&jumped))return;
        releaseDisplayed();
        requested=from==null?null:to;displayed=null;owned=false;start=from;probe=verify;bankTargets=candidates;ticks=0;status="";
        queryId=null;targetId=null;refreshPending=false;hints=Collections.emptyList();
        if(to==null || from==null)return;
        if(verify)
        {
            queryId="farming-helper-query-"+SEQUENCE.incrementAndGet();
            status="Checking route…";
            post("query",Map.of("id",queryId,"start",from,"target",candidates.isEmpty()?to:candidates));
            targetId="farming-helper-probe-"+SEQUENCE.incrementAndGet();
            post("getTarget",Map.of("id",targetId));
        }
        else {display(to);refreshPending=true;}
    }
    private void releaseDisplayed()
    {
        if(!enabled||!owned||displayed==null)return;
        // No exclusive ownership token exists in the public API. Even legacy
        // clients must answer an ownership check; silence never permits clear.
        clearId="farming-helper-clear-"+SEQUENCE.incrementAndGet();
        clearTarget=displayed;clearAge=0;
        post("getTarget",Map.of("id",clearId));
    }
    private void display(WorldPoint point)
    {
        if(start==null||point==null)return;
        clearId=null;clearTarget=null;
        displayed=point;owned=true;status="";
        post("path",Map.of("start",start,"target",point));
    }
    // Only one destination is owned; never clear a different or multi-target route.
    static boolean matchesTarget(Object expected,Object actual)
    {
        if(actual instanceof Collection)
        {
            Collection<?> targets=(Collection<?>)actual;
            return targets.size()==1&&Objects.equals(expected,targets.iterator().next());
        }
        return Objects.equals(expected,actual);
    }
    void tick(WorldPoint from)
    {
        if(clearId!=null&&++clearAge>=5){clearId=null;clearTarget=null;}
        if(!enabled||requested==null)return;
        if(from!=null)start=from;
        ticks++;
        if(queryId!=null && ticks>=(modern?30:5))
        {
            // No response: installed version may predate query. One legacy request only.
            queryId=null;
            if(modern&&bankTargets.isEmpty())status="Route check did not finish. Follow the guide’s transport instruction.";
            else display(requested);
            return;
        }
        if(refreshPending && ticks>=1)
        {
            refreshPending=false;
            targetId="farming-helper-target-"+SEQUENCE.incrementAndGet();
            post("getTarget",Map.of("id",targetId));
        }
        else if(modern && owned && displayed!=null && ticks%10==0)
        {
            targetId="farming-helper-owner-"+SEQUENCE.incrementAndGet();
            post("getTarget",Map.of("id",targetId));
        }
    }
    boolean message(PluginMessage event)
    {
        if(!enabled||!"shortestpath".equals(event.getNamespace())||event.getData()==null)return false;
        Map<String,Object> data=event.getData();Object id=data.get("id");
        if("currentTarget".equals(event.getName())&&clearId!=null&&clearId.equals(id))
        {
            Object expected=clearTarget;clearId=null;clearTarget=null;
            modern=true;
            if(owns(expected,data))post("clear",null);
            return true;
        }
        if("result".equals(event.getName()) && queryId!=null && queryId.equals(id))
        {
            modern=true;queryId=null;targetId=null;
            hints=TransportHint.read(data.get("transports"));
            if(!bankTargets.isEmpty())
            {
                Object target=data.get("target");
                // Multi-target support is optional. Never display a returned
                // point that was not one of our verified bank approaches.
                if(Boolean.TRUE.equals(data.get("reached"))&&target instanceof WorldPoint&&bankTargets.contains(target))
                    display((WorldPoint)target);
                else {hints=Collections.emptyList();display(requested);}
                return true;
            }
            if(Boolean.TRUE.equals(data.get("reached"))){display(requested);return true;}
            // A small reachable approach is preferable to a blocked object tile.
            Object near=data.get("closest");
            if(near instanceof WorldPoint && ((WorldPoint)near).getPlane()==requested.getPlane()
                && ((WorldPoint)near).distanceTo(requested)<=6 && data.get("path") instanceof Collection
                && !((Collection<?>)data.get("path")).isEmpty())
            {display((WorldPoint)near);status="Route ends at a nearby approach; follow the highlighted interaction.";return true;}
            status="No verified route for this leg. Follow the guide's entrance or transport instruction.";
            return true;
        }
        else if("currentTarget".equals(event.getName()) && targetId!=null && targetId.equals(id))
        {
            modern=true;boolean refresh=targetId.startsWith("farming-helper-target-");targetId=null;
            if(queryId!=null)return true;
            boolean same=owns(displayed,data);
            // Respect a user's cancellation or another helper replacing the route.
            if(!same){owned=false;hints=Collections.emptyList();return true;}
            // One fresh calculation after the next game tick so Shortest Path has
            // had a chance to refresh its inventory/equipment transport facts.
            if(refresh)display(displayed);
            return true;
        }
        return false;
    }
    private static boolean owns(Object expected,Map<String,Object> data)
    {
        Object overrides=data.get("config");
        return expected!=null&&Boolean.TRUE.equals(data.get("set"))&&matchesTarget(expected,data.get("target"))&&
            (overrides==null||overrides instanceof Map&&((Map<?,?>)overrides).isEmpty());
    }
    private void post(String action,Map<String,Object> data)
    {send.accept(data==null?new PluginMessage("shortestpath",action):new PluginMessage("shortestpath",action,data));}
}
