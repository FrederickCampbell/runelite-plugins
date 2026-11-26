package com.farminghelper;

import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.HashMap;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import java.util.function.IntFunction;
import net.runelite.api.ItemComposition;

/** Derived quantities for one observation of inventory, worn items and storage. */
final class PhysicalStock
{
    final Map<Integer,Integer> bag,equipped,carried,storage,stock,aliases,allocation,bankAndBag,travel,shelf;

    PhysicalStock(Map<Integer,Integer> bag,Map<Integer,Integer> equipped,Map<Integer,Integer> bank,
        Map<Integer,Integer> vault,IntFunction<String> names,IntUnaryOperator canonicalWorn)
    {
        this.bag=bag;
        this.equipped=equipped;
        carried=combine(bag,equipped);
        storage=combine(bank,vault);
        stock=combine(bag,storage);
        aliases=TreePreparation.aliases(stock,names);
        allocation=aliases.isEmpty()?stock:TreePreparation.allocationStock(stock,aliases);
        bankAndBag=combine(bank,bag);
        travel=combine(bankAndBag,equipped);
        shelf=new HashMap<>(bankAndBag);
        equipped.forEach((id,n)->shelf.merge(canonicalWorn.applyAsInt(id),n,Integer::sum));
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
        Map<Integer,Integer> stock = new HashMap<>();
        Item[] items = container == null ? null : container.getItems();
        if (items != null) for (Item item : items)
        {
            if (item == null || item.getId() <= 0) continue;
            ItemComposition def = definition.apply(item.getId());
            if (def == null) continue;
            if (def.getPlaceholderTemplateId() != -1)
            {
                if (placeholders != null && def.getPlaceholderId() > 0) placeholders.add(def.getPlaceholderId());
            }
            else if (item.getQuantity() > 0) stock.merge(item.getId(),item.getQuantity(),Integer::sum);
        }
        return stock;
    }
    static Map<Integer,Integer> combine(Map<Integer,Integer> a,Map<Integer,Integer> b)
    {
        Map<Integer,Integer> result=new HashMap<>(a);
        b.forEach((id,n)->result.merge(id,n,Integer::sum));
        return result;
    }
}
