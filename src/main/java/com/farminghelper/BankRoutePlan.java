package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;

/** One bank visit, one outstanding read-only Shortest Path query. Bank-origin
 * estimates rank actual owned item transports, not a hypothetical full loadout. */
final class BankRoutePlan
{
    private WorldPoint origin;
    private final Map<String,WorldPoint> remaining=new LinkedHashMap<>();
    private final Map<String,List<String>> routes=new LinkedHashMap<>();
    private Map<String,List<String>> resolved=Map.of();
    private String pending,site;
    private int age,budget;
    private boolean started,finished,frozen,initialPublished,finalPublished;
    private List<Integer> published=List.of();
    void reset()
    {
        origin=null;remaining.clear();routes.clear();resolved=Map.of();pending=null;site=null;
        age=budget=0;started=finished=frozen=initialPublished=finalPublished=false;published=List.of();
    }
    void freeze(){frozen=true;}
    void closeVisit(){remaining.clear();routes.clear();pending=null;site=null;started=false;finished=false;frozen=false;initialPublished=false;finalPublished=false;published=List.of();}
    Map<String,List<String>> routes(){return resolved;}
    private void finish(){finished=true;if(!frozen)resolved=Map.copyOf(routes);}
    boolean frozen(){return frozen;}
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
            if(point!=null)remaining.putIfAbsent(RunOrder.site(patch),point);
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
        site=next.getKey();WorldPoint target=next.getValue();remaining.remove(site);
        pending=navigation.bankQuery(origin,target);age=0;
        if(pending==null)finish();
    }
    boolean message(Map<String,Object> data)
    {
        if(pending==null||!pending.equals(data.get("id")))return false;
        pending=null;
        if(Boolean.TRUE.equals(data.get("reached")))routes.put(site,descriptions(data.get("transports")));
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
        if(!manual&&(frozen&&initialPublished||finalPublished))return published;
        initialPublished=true;finalPublished=finished;
        if(manual&&finished&&!routes.isEmpty())resolved=Map.copyOf(routes);
        return published=List.copyOf(supplies.keySet());
    }
}
