package com.farminghelper;

import java.util.*;
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
    // Highest capacity first, then open before closed for the same capacity.
    private static final List<Choice> SACKS=List.of(
        new Choice(ItemID.SLAYER_HERB_SACK_SILK_OPEN,100,"silklined herb sack",true),
        new Choice(ItemID.SLAYER_HERB_SACK_SILK,100,"silklined herb sack",false),
        new Choice(ItemID.SLAYER_HERB_SACK_OPEN,30,"herb sack",true),
        new Choice(ItemID.SLAYER_HERB_SACK,30,"herb sack",false));
    private static final List<Choice> POUCHES=List.of(
        new Choice(ItemID.MM_SECONDARY_POUCH_OPEN,26,"reagent pouch",true),
        new Choice(ItemID.MM_SECONDARY_POUCH,26,"reagent pouch",false));

    static Map<Kind,Choice> find(Map<Integer,Integer> stock)
    {
        Map<Kind,Choice> result=new EnumMap<>(Kind.class);
        for(Choice sack:SACKS)if(stock.getOrDefault(sack.id,0)>0){result.put(Kind.HERB,sack);break;}
        for(Choice pouch:POUCHES)if(stock.getOrDefault(pouch.id,0)>0){result.put(Kind.REAGENT,pouch);break;}
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
