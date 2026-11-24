package com.farminghelper;

import java.util.Map;
import java.util.HashMap;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import java.util.function.IntFunction;
import net.runelite.api.ItemComposition;

/** Placeholder items may have the same display name as a real seed.
 * Test the item's own definition, not its linked ordinary item. */
final class PhysicalStock
{
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
    static void collect(Map<Integer,Integer> stock,ItemContainer container,IntFunction<ItemComposition> definition)
    {
        if(container==null||container.getItems()==null)return;
        for(Item item:container.getItems())
            if(item!=null&&available(item.getId(),item.getQuantity(),definition))
                stock.merge(item.getId(),item.getQuantity(),Integer::sum);
    }
    static Map<Integer,Integer> combine(Map<Integer,Integer> a,Map<Integer,Integer> b)
    {
        Map<Integer,Integer> result=new HashMap<>(a);
        b.forEach((id,n)->result.merge(id,n,Integer::sum));
        return result;
    }
    private PhysicalStock(){}
}
