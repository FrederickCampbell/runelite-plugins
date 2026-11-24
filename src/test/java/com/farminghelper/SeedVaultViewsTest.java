package com.farminghelper;

import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SeedVaultViewsTest
{
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
