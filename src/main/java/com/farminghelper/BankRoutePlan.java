package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;

/** One bank visit, one outstanding read-only Shortest Path query. Bank-origin
 * estimates rank actual owned item transports, not a hypothetical full loadout. */
final class BankRoutePlan
{
    private WorldPoint origin;
    private final Map<String,WorldPoint> remaining=new LinkedHashMap<>();
    private final Map<String,Set<String>> routes=new LinkedHashMap<>();
    private String pending,site;
    private int age,budget;
    private boolean started,finished,frozen,initialPublished,finalPublished;
    private List<Integer> published=List.of();
    void reset()
    {
        origin=null;remaining.clear();routes.clear();pending=null;site=null;
        age=budget=0;started=finished=frozen=initialPublished=finalPublished=false;published=List.of();
    }
    void freeze(){frozen=true;}
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
        {pending=null;finished=true;return;}
        if(pending!=null||!navigation.bankQueryReady())return;
        if(remaining.isEmpty()){finished=true;return;}
        var next=remaining.entrySet().iterator().next();
        site=next.getKey();WorldPoint target=next.getValue();remaining.remove(site);
        pending=navigation.bankQuery(origin,target);age=0;
        if(pending==null)finished=true;
    }
    boolean message(Map<String,Object> data)
    {
        if(pending==null||!pending.equals(data.get("id")))return false;
        pending=null;
        if(Boolean.TRUE.equals(data.get("reached")))routes.put(site,descriptions(data.get("transports")));
        if(remaining.isEmpty())finished=true;
        return true;
    }
    /** Only item/spell edges lack an object interaction; exact shelf labels
     * subsequently distinguish known items from spells and POH furniture. */
    static Set<String> descriptions(Object raw)
    {
        Set<String> labels=new LinkedHashSet<>();
        if(raw instanceof Collection)for(Object edge:(Collection<?>)raw)
        {
            if(!(edge instanceof Map))continue;
            Map<?,?> m=(Map<?,?>)edge;Object object=m.get("objectInfo"),display=m.get("displayInfo");
            if((object==null||object instanceof String&&((String)object).isBlank())&&display instanceof String&&!((String)display).isBlank())
                labels.add((String)display);
        }
        return labels;
    }
    List<Integer> priority(Map<Integer,List<String>> eligible,Map<Integer,Integer> available,
        Map<Integer,Integer> carried,Map<Integer,Set<String>> fallback,boolean manual)
    {
        if(!manual&&(frozen&&initialPublished||finalPublished))return published;
        if(!finished&&!manual)
        {
            if(!initialPublished){published=List.copyOf(fallback.keySet());initialPublished=true;}
            return published;
        }
        initialPublished=true;finalPublished=finished;
        LinkedHashSet<Integer> result=new LinkedHashSet<>();
        for(Set<String> labels:routes.values())for(String label:labels)
        {
            int best=-1,score=Integer.MAX_VALUE;
            for(var e:eligible.entrySet())
            {
                int id=e.getKey();if(available.getOrDefault(id,0)<=0||!e.getValue().contains(label))continue;
                int rank=carried.getOrDefault(id,0)>0?0:result.contains(id)?1:2;
                if(rank<score||rank==score&&id<best){best=id;score=rank;}
            }
            if(best>0)result.add(best);
        }
        // The existing static fallback remains for legs with no successful reply.
        fallback.forEach((id,sites)->{if(sites.stream().anyMatch(s->!routes.containsKey(s)))result.add(id);});
        return published=List.copyOf(result);
    }
}
