package com.farminghelper;
import java.util.*;
import net.runelite.api.ItemComposition;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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
    @Test public void ownershipUsesExactNativeNamesAndOpenAction()
    {
        ItemComposition sack=mock(ItemComposition.class),silk=mock(ItemComposition.class),pouch=mock(ItemComposition.class),other=mock(ItemComposition.class);
        when(sack.getName()).thenReturn("Herb sack");
        when(silk.getName()).thenReturn("Silklined herb sack");
        when(silk.getInventoryActions()).thenReturn(new String[]{"Fill","Close"});
        when(pouch.getName()).thenReturn("Reagent pouch (open)");
        when(other.getName()).thenReturn("Herb sack ornament");
        var defs=Map.of(1,sack,2,silk,3,pouch,4,other);
        var found=HarvestContainers.find(Map.of(1,1,2,1,3,1,4,1),defs::get);
        assertEquals(100,found.get(HarvestContainers.Kind.HERB).capacity);
        assertTrue(found.get(HarvestContainers.Kind.HERB).open);
        assertEquals(26,found.get(HarvestContainers.Kind.REAGENT).capacity);
        assertTrue(found.get(HarvestContainers.Kind.REAGENT).open);
        assertEquals(30,HarvestContainers.find(Map.of(1,1),defs::get).get(HarvestContainers.Kind.HERB).capacity);
        assertTrue(HarvestContainers.find(Map.of(4,1),defs::get).isEmpty());
    }
}
