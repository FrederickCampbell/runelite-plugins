package com.farminghelper;

import java.util.*;
import java.util.function.Function;

/** Pure selection of existing vault item ids; seed and seedling aliases never become
 * plantable saplings or inflate the guide's plant-ready inventory. */
final class VaultRanking
{
    enum View
    {
        HIGHEST("Highest XP"), TOP_THREE("Top 3"), CUSTOM("Custom View"), RUN("Active Run");
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
        return direct!=null?direct:TreePreparation.material(itemName);
    }

    /** Distinct canonical crops, but all of the chosen crop's stocked native
     * seed/sapling representations remain visible. No stock is mutated. */
    static Set<Integer> select(View view,List<Entry> entries,int level,Set<PatchType> allowed,
        Function<PatchType,String> priorities,Map<PatchType,Set<Crop>> overrides)
    {
        if(view==View.RUN)return Collections.emptySet();
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
                for(Crop c:CropCatalog.preferences(type,priorities.apply(type)))if(!order.contains(c))order.add(c);
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
    /** Exact guide items plus raw seeds corresponding to allocated saplings. */
    static Set<Integer> selectRun(List<Entry> entries,Set<Integer> required)
    {
        Set<Integer> result=new LinkedHashSet<>();
        for(Entry entry:entries)if(entry.quantity>0&&(required.contains(entry.id)||
            entry.crop!=null&&entry.crop.getName().endsWith(" sapling")&&required.contains(entry.crop.getItemId())))result.add(entry.id);
        return result;
    }
    private VaultRanking(){}
}
