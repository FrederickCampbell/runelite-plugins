package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.timetracking.farming.Produce;

/** Advisory only: a container item proves ownership, not remaining capacity. */
final class HarvestContainers
{
    enum Kind { HERB, REAGENT }
    static final class Choice
    {
        final int id,capacity;
        final String name;
        final boolean open;
        Choice(int id,int capacity,String name,boolean open)
        {this.id=id;this.capacity=capacity;this.name=name;this.open=open;}
    }
    private static final Set<Integer> SECONDARIES=Set.of(ItemID.LIMPWURT_ROOT,ItemID.SNAPE_GRASS,
        ItemID.WHITE_BERRIES,ItemID.JANGERBERRIES,ItemID.POISONIVY_BERRIES,ItemID.CACTUS_SPINE,ItemID.CACTUS_POTATO);
    static Map<Kind,Choice> find(Map<Integer,Integer> stock,IntFunction<ItemComposition> definitions)
    {
        Map<Kind,Choice> result=new EnumMap<>(Kind.class);
        for(var entry:stock.entrySet())
        {
            if(entry.getValue()<=0)continue;
            ItemComposition d=definitions.apply(entry.getKey());
            if(d==null||d.getName()==null)continue;
            String name=d.getName().toLowerCase(Locale.ROOT).trim();
            boolean open=name.endsWith(" (open)")||name.startsWith("open ");
            if(name.endsWith(" (open)"))name=name.substring(0,name.length()-7);
            if(name.startsWith("open "))name=name.substring(5);
            Kind kind;
            int capacity;
            switch(name)
            {
                case "herb sack":kind=Kind.HERB;capacity=30;break;
                case "silklined herb sack":kind=Kind.HERB;capacity=100;break;
                case "reagent pouch":kind=Kind.REAGENT;capacity=26;break;
                default:continue;
            }
            String[] actions=d.getInventoryActions();
            if(actions!=null)for(String action:actions)if("Close".equalsIgnoreCase(action))open=true;
            Choice previous=result.get(kind);
            if(previous==null||capacity>previous.capacity||capacity==previous.capacity&&open&&!previous.open)
                result.put(kind,new Choice(entry.getKey(),capacity,name,open));
        }
        return result;
    }
    static boolean eligible(Kind kind,PatchType type,Produce produce)
    {
        if(produce==null)return false;
        if(kind==Kind.HERB)return type==PatchType.HERB&&!produce.name().equals("GOUTWEED")&&!produce.name().equals("ANYHERB");
        return SECONDARIES.contains(produce.getItemID());
    }
    /** Use the carried variant first; never request another copy or credit
     * unobserved capacity. A reagent pouch can help repeated same-type harvests. */
    static Choice required(Kind kind,boolean enabled,Choice carried,Choice bank,int species,int harvests)
    {
        if(!enabled||species<2&&(kind==Kind.HERB||harvests<2))return null;
        return carried!=null?carried:bank;
    }
    private HarvestContainers(){}
}
