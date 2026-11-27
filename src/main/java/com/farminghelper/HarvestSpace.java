package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;
import net.runelite.api.ItemComposition;
import net.runelite.client.plugins.timetracking.farming.Produce;

/** Advisory inventory relief, not a deposit or item interaction. Only known
 * noteable farm yields; never reserve or consume a payment or planting item. */
final class HarvestSpace
{
    private static final Set<Integer> PRODUCE=new HashSet<>();
    private static final Set<String> GRIMY=new HashSet<>();
    static
    {
        for(Produce p:Produce.values())
        {
            if(p.getPatchImplementation()==null)continue;
            switch(p.getPatchImplementation().name())
            {
                case "HERB":
                    if(p.name().equals("GOUTWEED")||p.name().equals("ANYHERB"))break;
                    GRIMY.add("grimy "+p.getName().toLowerCase(Locale.ROOT));
                    // fall through: clean herbs are noteable too
                case "ALLOTMENT":case "FLOWER":case "BUSH":case "HOPS":case "FRUIT_TREE":
                case "CACTUS":case "SEAWEED":case "GRAPES":case "MUSHROOM":case "CALQUAT":
                    PRODUCE.add(p.getItemID());break;
                default:break;
            }
        }
        // Native herb names and Produce's display names are not always identical.
        Collections.addAll(GRIMY,"grimy guam leaf","grimy ranarr weed","grimy irit leaf");
    }
    /** Native farming_tools_getstored packs empty bucket counts into 3 varbits. */
    static int depositBuckets(Map<Integer,Integer> bag,Map<Integer,Integer> reserved,int low,int middle,int high)
    {
        if(reserved.getOrDefault(CompostRun.BUCKET,0)>0)return 0;
        int carried=bag.getOrDefault(CompostRun.BUCKET,0);
        return Math.max(0,Math.min(carried,1000-(low+32*middle+256*high)));
    }
    static int best(Map<Integer,Integer> bag,Map<Integer,Integer> reserved,String location,IntFunction<ItemComposition> definitions)
    {
        int best=-1,saved=0;
        for(var e:bag.entrySet())
        {
            int id=e.getKey(),count=e.getValue();
            if(count<=0||reserved.getOrDefault(id,0)>0)continue;
            ItemComposition item=definitions.apply(id);
            if(item==null||item.getNote()!=-1||item.getLinkedNoteId()<=0||item.isStackable())continue;
            String name=item.getName();
            if(name==null||"Falador".equals(location)&&"Cabbage".equalsIgnoreCase(name))continue;
            if(!PRODUCE.contains(id)&&!GRIMY.contains(name.toLowerCase(Locale.ROOT)))continue;
            int gain=count-(bag.getOrDefault(item.getLinkedNoteId(),0)>0?0:1);
            if(gain>saved||gain==saved&&gain>0&&id<best){best=id;saved=gain;}
        }
        return best;
    }
    private HarvestSpace(){}
}
