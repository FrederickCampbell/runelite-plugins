package com.farminghelper;

import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuidePresentation50Test
{
    @Test public void storageWarningUpdatesIndependently()
    {
        long now=10000000L;
        assertTrue(GuidePresentation.stale(0,now));
        assertTrue(GuidePresentation.stale(now-GuidePresentation.STORAGE_FRESH_MS,now));
        assertFalse(GuidePresentation.stale(now-1000,now));
        assertEquals("Open bank and Seed Vault to refresh stock.",GuidePresentation.storageWarning(true,true));
        assertEquals("Open your bank to refresh stock.",GuidePresentation.storageWarning(true,false));
        assertEquals("Open the Seed Vault to refresh stock.",GuidePresentation.storageWarning(false,true));
        assertEquals("",GuidePresentation.storageWarning(false,false));
    }

    @Test public void deferredPatchesNeverFillProgress()
    {
        GuideView.Stats stats=new GuideView.Stats(0,0,6,0,13,7,0);
        assertEquals(0,GuidePresentation.done(stats,99));
        assertEquals(7,GuidePresentation.remaining(stats,99));
        GuideView.Stats later=new GuideView.Stats(3,1,6,0,13,2,1);
        assertEquals(4,GuidePresentation.done(later,99));
        assertEquals(2,GuidePresentation.remaining(later,99));
    }

    @Test public void preparationAndContextAreShortAndSeparate() throws Exception
    {
        assertEquals("Collect supplies: Farming Guild Seed Vault.",GuidePresentation.preparation(true,"Farming Guild Seed Vault",false));
        assertEquals("",GuidePresentation.routeWarning("Checking route…"));
        assertFalse(GuidePresentation.routeWarning("No verified route for this leg.").isEmpty());
        SwingUtilities.invokeAndWait(()->{
            JButton info=FarmingHelperPanel.infoButton(GuidePresentation.STORAGE_HELP);
            assertNotNull(info.getIcon());
            assertTrue(info.getToolTipText().contains("refresh")||info.getToolTipText().contains("Refresh"));
            assertEquals("More information",info.getAccessibleContext().getAccessibleName());
        });
    }
}
