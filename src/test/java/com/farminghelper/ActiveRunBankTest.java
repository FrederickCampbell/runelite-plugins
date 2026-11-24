package com.farminghelper;
import java.util.Set;
import net.runelite.api.ItemComposition;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class ActiveRunBankTest
{
    @Test public void admitsOnlyIdsAlreadyInSuppliedNativeLayout()
    {
        assertEquals(Set.of(1513,1755),ActiveRunBank.admittedIds(new int[]{-1,1513,1755,-1,1513}));
        assertFalse(ActiveRunBank.admittedIds(new int[]{1513,-1}).contains(1755));
    }
    @Test public void filtersExactPhysicalItemIds()
    {
        ItemComposition real=mock(ItemComposition.class),placeholder=mock(ItemComposition.class);
        when(real.getPlaceholderTemplateId()).thenReturn(-1);
        when(placeholder.getPlaceholderTemplateId()).thenReturn(14401);
        assertTrue(ActiveRunBank.includes(Set.of(1513,1755),1513,real));
        assertFalse(ActiveRunBank.includes(Set.of(1513),1513,placeholder));
        assertFalse(ActiveRunBank.includes(Set.of(1513),1515,real));
    }
}
