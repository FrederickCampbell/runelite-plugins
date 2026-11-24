package com.farminghelper;

import java.util.Map;
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
    private PhysicalStock(){}
}
