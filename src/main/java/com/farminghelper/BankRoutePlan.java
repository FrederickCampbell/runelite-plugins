package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;

/** One bank visit, one outstanding read-only Shortest Path query. Bank-origin
 * estimates rank actual owned item transports, not a hypothetical full loadout. */
final class BankRoutePlan
{
    private WorldPoint origin;
    private final Map<String,WorldPoint> remaining=new LinkedHashMap<>(), exits=new HashMap<>();
    private final Map<String,List<String>> routes=new LinkedHashMap<>();
    private Map<String,List<String>> resolved=Map.of();
    private String pending,site;
    private int age,budget;
    private boolean started,finished;
    private int retries;
    private WorldPoint pendingTarget;
    private List<Integer> published=List.of();
    void reset()
    {
        closeVisit();resolved=Map.of();
    }
    void closeVisit()
    {
        origin=null;remaining.clear();exits.clear();routes.clear();pending=null;site=null;
        age=budget=retries=0;pendingTarget=null;started=finished=false;published=List.of();
    }
    Map<String,List<String>> routes(){return resolved;}
    private void finish(){finished=true;resolved=Map.copyOf(routes);}
    boolean started(){return started;}
    boolean finished(){return finished;}
    static boolean bankMode(String mode)
    {return "INVENTORY_AND_BANK".equalsIgnoreCase(mode)||"INVENTORY_AND_BANK_NON_CONSUMABLE".equalsIgnoreCase(mode);}
    void begin(WorldPoint from,List<FarmRunPatch> patches)
    {
        reset();started=true;origin=from;
        for(FarmRunPatch patch:patches)
        {
            WorldPoint point=RunOrder.surface(patch);
            if(point!=null){String key=RunOrder.site(patch);remaining.putIfAbsent(key,point);exits.put(key,point);}
        }
        finished=from==null||remaining.isEmpty();
    }
    void tick(NavigationBridge navigation)
    {
        if(!started||finished)return;
        if(++budget>=100||pending!=null&&++age>=30)
        {pending=null;finish();return;}
        if(pending!=null||!navigation.bankQueryReady())return;
        if(remaining.isEmpty()){finish();return;}
        var next=remaining.entrySet().iterator().next();
        site=next.getKey();pendingTarget=next.getValue();remaining.remove(site);
        pending=navigation.bankQuery(origin,pendingTarget);age=0;
        if(pending==null)finish();
    }
    boolean message(Map<String,Object> data)
    {
        if(pending==null||!pending.equals(data.get("id")))return false;
        pending=null;
        // A displayed path can cancel a queued background query. Retry once, within the original budget.
        if("CANCELLED".equals(data.get("reason"))&&retries++==0)
        {
            Map<String,WorldPoint> tail=new LinkedHashMap<>(remaining);
            remaining.clear();remaining.put(site,pendingTarget);remaining.putAll(tail);
            return true;
        }
        retries=0;
        if(Boolean.TRUE.equals(data.get("reached"))&&data.get("transports") instanceof Collection)
        {
            routes.put(site,descriptions(data.get("transports")));
            // Publish each completed leg, even while the player is withdrawing supplies.
            resolved=Map.copyOf(routes);
        }
        // Estimate each leg from the preceding visit; this does not simulate inventory.
        origin=exits.getOrDefault(site,origin);
        if(remaining.isEmpty())finish();
        return true;
    }
    /** Preserve spell and item labels, fairy-ring dependencies and repeated
     * transport occurrences; ordinary object interactions are not item IDs. */
    static List<String> descriptions(Object raw)
    {
        List<String> labels=new ArrayList<>();
        if(raw instanceof Collection)for(Object edge:(Collection<?>)raw)
        {
            if(!(edge instanceof Map))continue;
            Map<?,?> m=(Map<?,?>)edge;Object object=m.get("objectInfo"),display=m.get("displayInfo");
            String action=Objects.toString(object,"").toLowerCase(Locale.ROOT);
            if(action.contains("fairy ring"))labels.add(RouteSupplies.FAIRY);
            else if(action.isBlank())
            {
                if(display instanceof String&&!((String)display).isBlank())labels.add((String)display);
                else labels.add(RouteSupplies.UNKNOWN);
            }
        }
        return labels;
    }
    List<Integer> priority(Map<Integer,Set<String>> supplies,boolean manual)
    {
        // Stable for unchanged dependency sets, never suppress newly verified items.
        if(!manual&&published.size()==supplies.size()&&supplies.keySet().containsAll(published))return published;
        return published=List.copyOf(supplies.keySet());
    }
}
