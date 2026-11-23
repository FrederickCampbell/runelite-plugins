package com.farminghelper;

import javax.swing.JPanel;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class WaypointAndLayoutTest
{
    @Test public void waypointRoundTripsAndRejectsMalformedCoordinates()
    {
        WorldPoint p = new WorldPoint(3058, 3311, 0);
        assertEquals(p, WaypointPins.decode(WaypointPins.encode(p)));
        assertNull(WaypointPins.decode("1:2:4"));
        assertNull(WaypointPins.decode("abc:2:0"));
        assertNull(WaypointPins.decode("1:20000:0"));
        TravelTarget target = TravelTarget.playerWaypoint(p);
        assertTrue(target.routable());
        assertFalse(target.exact);
    }

    @Test public void scrollViewportTracksWidthAndKeepsNaturalHeight()
    {
        SidebarViewport viewport = new SidebarViewport(new JPanel());
        assertTrue(viewport.getScrollableTracksViewportWidth());
        assertFalse(viewport.getScrollableTracksViewportHeight());
    }
}
