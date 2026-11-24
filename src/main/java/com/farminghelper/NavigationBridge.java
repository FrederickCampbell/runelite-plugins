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
    private WorldPoint requested, displayed, start, fallbackBank;
    private Map<Integer,Integer> bankRequirements=Map.of();
    private Object shownTarget;
    private boolean enabled, owned, probe, refreshPending, modern;
    private int ticks, sequence;
    private String queryId, targetId, status="";
    private List<TransportHint> hints=Collections.emptyList();
    TransportHint nearbyTransport(WorldPoint player){return enabled&&owned?TransportHint.nearby(hints,player):null;}
    NavigationBridge(Consumer<PluginMessage> send){this.send=send;}
    void enabled(boolean value){if(enabled!=value){reset(false);modern=false;enabled=value;}}
    String status(){return status;}
    // Only use background ordering queries after the installed API has responded
    // and the active navigation request is no longer awaiting its own probe.
    boolean canProbeOrder(){return enabled&&modern&&queryId==null&&!refreshPending;}
    void begin(){reset(true);}
    void stop(){reset(true);}
    private void reset(boolean clear)
    {
        if(clear && enabled && owned && displayed!=null)post("clear",null);
        requested=null;displayed=null;start=null;owned=false;probe=false;refreshPending=false;
        fallbackBank=null;bankRequirements=Map.of();shownTarget=null;
        queryId=null;targetId=null;status="";ticks=0;hints=Collections.emptyList();
    }
    void request(WorldPoint from,WorldPoint to,boolean verify)
    {request(from,to,verify,null,Map.of());}
    void requestBank(WorldPoint from,WorldPoint patch,WorldPoint fallback,Map<Integer,Integer> requirements)
    {request(from,patch,true,fallback,Map.copyOf(requirements));}
    private void request(WorldPoint from,WorldPoint to,boolean verify,WorldPoint fallback,Map<Integer,Integer> requirements)
    {
        if(!enabled)return;
        if(Objects.equals(requested,to)&&probe==verify&&Objects.equals(fallbackBank,fallback)&&bankRequirements.equals(requirements))return;
        if(owned && displayed!=null)post("clear",null);
        requested=from==null?null:to;displayed=null;owned=false;start=from;probe=verify;ticks=0;status="";
        fallbackBank=fallback;bankRequirements=requirements;shownTarget=null;
        queryId=null;targetId=null;refreshPending=false;hints=Collections.emptyList();
        if(to==null || from==null)return;
        if(verify)
        {
            queryId="farming-helper-query-"+(++sequence);
            status="Checking route…";
            Object target=bankRequirements.isEmpty()?to:Map.of("point",to,"requirements",requirementGroups(bankRequirements));
            post("query",Map.of("id",queryId,"start",from,"target",target));
            targetId="farming-helper-probe-"+(++sequence);
            post("getTarget",Map.of("id",targetId));
        }
        else {display(to);refreshPending=true;}
    }
    private void display(WorldPoint point){display(point,point);}
    private void display(WorldPoint point,Object target)
    {
        if(start==null||point==null)return;
        displayed=point;shownTarget=target;owned=true;status="";
        post("path",Map.of("start",start,"target",target));
    }
    static List<Map<Integer,Integer>> requirementGroups(Map<Integer,Integer> requirements)
    {
        List<Map<Integer,Integer>> result=new ArrayList<>();
        requirements.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->result.add(Map.of(e.getKey(),e.getValue())));
        return result;
    }
    private void bankFallback()
    {
        display(fallbackBank);
        status="Use the suggested bank or any accessible bank, then continue.";
    }
    // CurrentTarget encodes requirements as a list of any-of maps, even when
    // the original request used a single map. Compare point and requirements.
    static boolean matchesTarget(Object expected,Object actual)
    {
        if(actual instanceof Collection)
        {
            Collection<?> targets=(Collection<?>)actual;
            return targets.size()==1&&matchesTarget(expected,targets.iterator().next());
        }
        if(expected instanceof Map&&actual instanceof Map)
        {
            Map<?,?> e=(Map<?,?>)expected,a=(Map<?,?>)actual;
            Object requirements=e.get("requirements"),returned=a.get("requirements");
            return Objects.equals(e.get("point"),a.get("point"))&&
                (Objects.equals(requirements,returned)||requirements instanceof Collection&&returned instanceof Collection&&
                    new HashSet<>((Collection<?>)requirements).equals(new HashSet<>((Collection<?>)returned)));
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
            if(fallbackBank!=null)bankFallback();
            else if(modern)status="Route check did not finish. Follow the guide’s transport instruction.";
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
            hints=TransportHint.read(data.get("transports"));
            if(fallbackBank!=null)
            {
                if(Boolean.TRUE.equals(data.get("reached"))&&"BANK".equals(data.get("targetStatus"))&&Boolean.TRUE.equals(data.get("bankVisited")))
                {
                    display(requested,Map.of("point",requested,"requirements",requirementGroups(bankRequirements)));
                    status="Follow the bank detour, then continue when ready.";
                }
                else bankFallback();
                return;
            }
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
