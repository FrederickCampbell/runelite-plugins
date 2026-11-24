package com.farminghelper;

import java.util.*;
import java.util.function.Function;

/** Pure selection of existing vault item ids; raw seed aliases never become
 * plantable saplings or inflate the guide's plant-ready inventory. */
final class VaultRanking
{
    enum View
    {
        HIGHEST("Highest XP"), TOP_THREE("Top 3"), CUSTOM("Custom View");
        final String label;
        View(String label){this.label=label;}
    }

    static final class Entry
    {
        final int id,quantity;
        final Crop crop;
        Entry(int id,int quantity,Crop crop){this.id=id;this.quantity=quantity;this.crop=crop;}
    }

    /** Resolve only recognized native seed names, never guessed item IDs. */
    static Crop resolve(int id,String itemName)
    {
        Crop direct=CropCatalog.findByItemId(id);
        if(direct!=null)return direct;
        if(itemName==null)return null;
        String name=itemName.trim().toLowerCase(Locale.ROOT);
        for(PatchType type:PatchType.values())for(Crop c:CropCatalog.crops(type))
        {
            if(!c.getName().endsWith(" sapling"))continue;
            String base=c.getName().substring(0,c.getName().length()-8).toLowerCase(Locale.ROOT);
            if(name.equals(base+" seed")||name.equals(base+" tree seed")||
                (base.equals("oak")&&name.equals("acorn"))||(base.equals("crystal")&&name.equals("crystal acorn")))return c;
        }
        return null;
    }

    /** Distinct canonical crops, but all of the chosen crop's stocked native
     * seed/sapling representations remain visible. No stock is mutated. */
    static Set<Integer> select(View view,List<Entry> entries,int level,Set<PatchType> allowed,
        Function<PatchType,String> priorities,Map<PatchType,Set<Crop>> overrides)
    {
        Map<Crop,Map<Integer,Integer>> stock=new LinkedHashMap<>();
        for(Entry entry:entries)
        {
            if(entry.crop==null||entry.quantity<=0||entry.crop.getLevel()>level||!allowed.contains(entry.crop.getPatchType()))continue;
            // A favourite may duplicate the same native item. Never sum duplicate widgets.
            stock.computeIfAbsent(entry.crop,c->new LinkedHashMap<>()).merge(entry.id,entry.quantity,Math::max);
        }
        Set<Integer> selected=new LinkedHashSet<>();
        for(PatchType type:allowed)
        {
            List<Crop> order=new ArrayList<>();
            if(view==View.CUSTOM)
            {
                for(Crop c:overrides.getOrDefault(type,Collections.emptySet()))if(!order.contains(c))order.add(c);
                for(String n:priorities.apply(type).split(","))
                {
                    Crop c=CropCatalog.findByName(type,n.trim());
                    if(c!=null&&!order.contains(c))order.add(c);
                }
            }
            else order.addAll(CropXp.ranked(type,level));
            int limit=view==View.HIGHEST?1:view==View.TOP_THREE?3:Integer.MAX_VALUE;
            int count=0;
            for(Crop crop:order)
            {
                Map<Integer,Integer> quantities=stock.get(crop);
                if(quantities==null||quantities.values().stream().mapToLong(Integer::longValue).sum()<crop.getQuantity())continue;
                selected.addAll(quantities.keySet());
                if(++count>=limit)break;
            }
        }
        return selected;
    }
    private VaultRanking(){}
}
