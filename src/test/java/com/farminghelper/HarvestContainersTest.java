package com.farminghelper;
import java.util.*;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class HarvestContainersTest
{
    @Test public void enabledUsefulOwnedContainerBecomesOneNormalSupply()
    {
        var herb=HarvestContainers.Kind.HERB;var reagent=HarvestContainers.Kind.REAGENT;
        var sack=new HarvestContainers.Choice(1,30,"herb sack",false);
        var silk=new HarvestContainers.Choice(2,100,"silklined herb sack",true);
        assertSame(silk,HarvestContainers.required(herb,true,silk,sack,2,2));
        assertSame(sack,HarvestContainers.required(herb,true,null,sack,2,2));
        assertNull(HarvestContainers.required(herb,false,null,sack,2,2));
        assertNull(HarvestContainers.required(herb,true,null,sack,1,4));
        assertNull(HarvestContainers.required(herb,true,null,null,3,3));
        var pouch=new HarvestContainers.Choice(3,26,"reagent pouch",false);
        assertSame(pouch,HarvestContainers.required(reagent,true,null,pouch,1,2));
        assertNull(HarvestContainers.required(reagent,true,null,pouch,1,1));
        assertEquals(Set.of(3),GuideView.missingItems(Map.of(pouch.id,1),Map.of()));
        assertTrue(GuideView.missingItems(Map.of(silk.id,1),Map.of(silk.id,1)).isEmpty());
    }
    @Test public void eachPhysicalVariantIsRecognizedAndPreferredInOrder()
    {
        int[] sacks={ItemID.SLAYER_HERB_SACK,ItemID.SLAYER_HERB_SACK_OPEN,
            ItemID.SLAYER_HERB_SACK_SILK,ItemID.SLAYER_HERB_SACK_SILK_OPEN};
        Map<Integer,Integer> stock=new HashMap<>();
        for(int i=0;i<sacks.length;i++)
        {
            stock.put(sacks[i],1);
            var best=HarvestContainers.find(stock).get(HarvestContainers.Kind.HERB);
            assertEquals(sacks[i],best.id);
            assertEquals(i>=2?100:30,best.capacity);
            assertEquals(i%2==1,best.open);
        }
        stock.put(ItemID.MM_SECONDARY_POUCH,1);
        assertFalse(HarvestContainers.find(stock).get(HarvestContainers.Kind.REAGENT).open);
        stock.put(ItemID.MM_SECONDARY_POUCH_OPEN,1);
        assertTrue(HarvestContainers.find(stock).get(HarvestContainers.Kind.REAGENT).open);
        assertTrue(HarvestContainers.find(Map.of(ItemID.SPADE,1,ItemID.SLAYER_HERB_SACK,0)).isEmpty());
    }
}
