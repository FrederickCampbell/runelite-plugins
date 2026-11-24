package com.farminghelper;

import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SeedVaultViewsTest
{
    @Test public void runFilterUsesExactRequestedIdsAndLiveStock()
    {
        java.util.List<VaultRanking.Entry> entries=java.util.List.of(
            new VaultRanking.Entry(100,2,null),new VaultRanking.Entry(101,20,null),
            new VaultRanking.Entry(102,0,null),new VaultRanking.Entry(103,1,null));
        assertEquals(java.util.Set.of(100,103),VaultRanking.selectRun(entries,java.util.Set.of(100,102,103)));
        assertEquals(java.util.Set.of(101),VaultRanking.selectRun(entries,java.util.Set.of(101)));
        assertTrue(VaultRanking.selectRun(entries,java.util.Set.of()).isEmpty());
    }
    @Test public void quantitiesOnlyChangeSelectionWhenRequiredIdsDisappear()
    {
        java.util.List<VaultRanking.Entry> entries=java.util.List.of(
            new VaultRanking.Entry(100,20,null),new VaultRanking.Entry(101,5,null));
        java.util.Set<Integer> required=java.util.Set.of(100,101);
        assertEquals(VaultRanking.selectRun(entries,required),
            VaultRanking.selectRun(SeedVaultViews.withQuantities(entries,java.util.Map.of(100,19,101,6)),required));
        assertEquals(java.util.Set.of(101),
            VaultRanking.selectRun(SeedVaultViews.withQuantities(entries,java.util.Map.of(101,6)),required));
    }
    @Test public void onlyMatchedNativePairsAreEligibleForFiltering()
    {
        Widget icon=mock(Widget.class),label=mock(Widget.class);
        when(icon.getIndex()).thenReturn(8);when(label.getIndex()).thenReturn(8);
        when(icon.getType()).thenReturn(WidgetType.GRAPHIC);when(label.getType()).thenReturn(WidgetType.TEXT);
        when(icon.getOriginalX()).thenReturn(14);when(icon.getOriginalY()).thenReturn(228);when(icon.getOriginalHeight()).thenReturn(32);
        when(label.getOriginalX()).thenReturn(7);when(label.getOriginalY()).thenReturn(260);
        assertTrue(SeedVaultViews.pair(icon,label));
        when(label.getIndex()).thenReturn(9);assertFalse(SeedVaultViews.pair(icon,label));
        when(label.getIndex()).thenReturn(8);when(label.getOriginalY()).thenReturn(200);assertFalse(SeedVaultViews.pair(icon,label));
        assertEquals("Magic sapling",SeedVaultViews.clean("<col=ff981f>Magic<br>sapling</col>"));
    }
}
