package com.farminghelper;

import java.util.*;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;

/** One character/session's last observed native seed-box contents.
 * The order is significant: using the box on a patch attempts the first compatible seed.
 * No inferred counts, menu automation, or persisted contents across sessions. */
final class SeedBoxStock
{
    private Map<Integer,Integer> observed=Map.of();
    private boolean known;
    static boolean isBox(int id){return id==ItemID.SEED_BOX||id==ItemID.SEED_BOX_OPEN;}
    static int boxId(Map<Integer,Integer> bag)
    {
        if(bag.getOrDefault(ItemID.SEED_BOX_OPEN,0)>0)return ItemID.SEED_BOX_OPEN;
        return bag.getOrDefault(ItemID.SEED_BOX,0)>0?ItemID.SEED_BOX:-1;
    }
    void clear(){observed=Map.of();known=false;}
    boolean known(){return known;}
    void observe(ItemContainer container)
    {
        if(container==null)return;
        Map<Integer,Integer> ordered=new LinkedHashMap<>();
        for(Item item:container.getItems())
            if(item!=null&&item.getId()>0&&item.getQuantity()>0)
                ordered.merge(item.getId(),item.getQuantity(),(x,y)->(int)Math.min(Integer.MAX_VALUE,(long)x+y));
        observed=Collections.unmodifiableMap(ordered);known=true;
    }
    Map<Integer,Integer> carried(Map<Integer,Integer> bag)
    {
        if(boxId(bag)<0){clear();return Map.of();}
        return observed;
    }
    static boolean direct(Crop target,Map<Integer,Integer> contents)
    {
        if(target==null)return false;
        for(var entry:contents.entrySet())
        {
            Crop crop=CropCatalog.findByItemId(entry.getKey());
            if(entry.getValue()>0&&crop!=null&&crop.getPatchType()==target.getPatchType())
                return crop.equals(target)&&entry.getValue()>=target.getQuantity();
        }
        return false;
    }
    static int slots(Map<Integer,Integer> required,Map<Integer,Integer> equipped,Map<Integer,Integer> box,
        java.util.function.IntPredicate stackable)
    {
        Map<Integer,Integer> loose=new HashMap<>(required);
        boolean used=false;
        for(var entry:box.entrySet())
        {
            int n=Math.min(loose.getOrDefault(entry.getKey(),0),entry.getValue());
            if(n>0){used=true;loose.computeIfPresent(entry.getKey(),(id,count)->count>n?count-n:null);}
        }
        return Preparation.slots(loose,equipped,stackable)+(used?1:0);
    }
}
