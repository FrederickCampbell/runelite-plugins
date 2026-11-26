package com.farminghelper;

import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.HashMap;
import java.util.HashSet;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import java.util.function.IntFunction;
import net.runelite.api.ItemComposition;

/** Derived quantities for one observation of inventory, worn items and storage. */
final class PhysicalStock
{
    final Map<Integer,Integer> bag,equipped,carried,storage,stock,aliases,allocation,bankAndBag,travel,shelf,seedBox,plantingBag;

    PhysicalStock(Map<Integer,Integer> bag,Map<Integer,Integer> equipped,Map<Integer,Integer> bank,
        Map<Integer,Integer> vault,IntFunction<String> names,IntUnaryOperator canonicalWorn)
    {
        this(bag,equipped,bank,vault,Map.of(),names,canonicalWorn);
    }

    PhysicalStock(Map<Integer,Integer> bag,Map<Integer,Integer> equipped,Map<Integer,Integer> bank,
        Map<Integer,Integer> vault,Map<Integer,Integer> seedBox,IntFunction<String> names,IntUnaryOperator canonicalWorn)
    {
        this.bag=Map.copyOf(bag);
        this.equipped=Map.copyOf(equipped);
        this.seedBox=SeedBoxStock.boxId(bag)>0?seedBox:Map.of();
        plantingBag=this.seedBox.isEmpty()?this.bag:combine(bag,this.seedBox);
        carried=combine(plantingBag,equipped);
        storage=combine(bank,vault);
        stock=combine(plantingBag,storage);
        aliases=TreePreparation.aliases(stock,names);
        allocation=aliases.isEmpty()?stock:TreePreparation.allocationStock(stock,aliases);
        Map<Integer,Integer> loose=combine(bank,bag);
        bankAndBag=this.seedBox.isEmpty()?loose:combine(loose,this.seedBox);
        travel=combine(loose,equipped);
        shelf=new HashMap<>(loose);
        equipped.forEach((id,n)->shelf.merge(canonicalWorn.applyAsInt(id),n,PhysicalStock::sum));
    }

    static boolean available(int id,int quantity,IntFunction<ItemComposition> definition)
    {
        if(id<=0||quantity<=0)return false;
        ItemComposition item=definition.apply(id);
        return item!=null&&item.getPlaceholderTemplateId()==-1;
    }
    static void sanitize(Map<Integer,Integer> stock,IntFunction<ItemComposition> definition)
    {
        stock.entrySet().removeIf(e->!available(e.getKey(),e.getValue(),definition));
    }
    static Map<Integer,Integer> read(ItemContainer container,IntFunction<ItemComposition> definition)
    {
        return read(container,definition,null);
    }
    /** Collect native placeholder base IDs for display separately from physical quantities. */
    static Map<Integer,Integer> read(ItemContainer container, IntFunction<ItemComposition> definition, Set<Integer> placeholders)
    {
        Map<Integer,Integer> stock=read(container,definition,placeholders,false);
        return stock==null?Map.of():stock;
    }
    /** Null means unavailable/incomplete; an empty map is a verified empty container.
     * Never replace a remembered storage snapshot with a partial decode. */
    static Map<Integer,Integer> readChecked(ItemContainer container,IntFunction<ItemComposition> definition,Set<Integer> placeholders)
    {
        return read(container,definition,placeholders,true);
    }
    private static Map<Integer,Integer> read(ItemContainer container,IntFunction<ItemComposition> definition,Set<Integer> placeholders,boolean strict)
    {
        Item[] items=container==null?null:container.getItems();
        if(items==null)return null;
        Map<Integer,Integer> stock=new HashMap<>();
        Set<Integer> ghosts=placeholders==null?null:new HashSet<>();
        for(Item item:items)
        {
            if (item == null || item.getId() <= 0) continue;
            ItemComposition def = definition.apply(item.getId());
            if(def==null){if(strict)return null;continue;}
            if (def.getPlaceholderTemplateId() != -1)
            {
                if (ghosts != null && def.getPlaceholderId() > 0) ghosts.add(def.getPlaceholderId());
            }
            else if (item.getQuantity() > 0) stock.merge(item.getId(),item.getQuantity(),PhysicalStock::sum);
        }
        if(placeholders!=null)placeholders.addAll(ghosts);
        return stock;
    }
    /** Highest usable rank wins. Equal ranks prefer the lowest physical item ID. */
    static int best(Map<Integer,Integer> stock, IntUnaryOperator rank, int minimum, int missing)
    {
        int best = missing, score = minimum;
        for (var stack : stock.entrySet())
        {
            if (stack.getValue() <= 0) continue;
            int id = stack.getKey(), candidate = rank.applyAsInt(id);
            if (candidate > score || (candidate == score && candidate > minimum && id < best))
            {
                best = id;
                score = candidate;
            }
        }
        return best;
    }
    static int sum(int a,int b){return (int)Math.min(Integer.MAX_VALUE,(long)a+b);}
    static Map<Integer,Integer> combine(Map<Integer,Integer> a,Map<Integer,Integer> b)
    {
        Map<Integer,Integer> result=new HashMap<>(a);
        b.forEach((id,n)->result.merge(id,n,PhysicalStock::sum));
        return result;
    }
}
