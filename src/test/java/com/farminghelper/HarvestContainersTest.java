package com.farminghelper;
import java.util.*;
import net.runelite.api.ItemComposition;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HarvestContainersTest
{
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
