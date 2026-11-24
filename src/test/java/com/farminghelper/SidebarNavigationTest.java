package com.farminghelper;
import org.junit.Test;
import static org.junit.Assert.*;
public class SidebarNavigationTest
{
    @Test public void liveUpdatesDoNotTakeOverSettings()
    {
        SidebarNavigation n=new SidebarNavigation();
        n.starting();assertTrue(n.acceptGuide());
        n.show(SidebarNavigation.Page.RUN_SETTINGS);assertFalse(n.acceptGuide());
        n.show(SidebarNavigation.Page.HOME);assertFalse(n.acceptGuide());
        n.show(SidebarNavigation.Page.GUIDE);assertTrue(n.acceptGuide());
        n.reset();assertFalse(n.acceptGuide());
    }
}
