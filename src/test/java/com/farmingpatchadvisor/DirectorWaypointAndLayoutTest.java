package com.farmingpatchadvisor;

import javax.swing.JPanel;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorWaypointAndLayoutTest
{
    @Test public void waypointRoundTripsAndRejectsMalformedCoordinates()
    {
        WorldPoint p = new WorldPoint(3058, 3311, 0);
        assertEquals(p, DirectorWaypointPins.decode(DirectorWaypointPins.encode(p)));
        assertNull(DirectorWaypointPins.decode("1:2:4"));
        assertNull(DirectorWaypointPins.decode("abc:2:0"));
        assertNull(DirectorWaypointPins.decode("1:20000:0"));
        DirectorTravelTarget target = DirectorTravelTarget.playerWaypoint(p);
        assertTrue(target.routable());
        assertFalse(target.exact);
    }

    @Test public void scrollViewportTracksWidthAndKeepsNaturalHeight()
    {
        DirectorSidebarViewport viewport = new DirectorSidebarViewport(new JPanel());
        assertTrue(viewport.getScrollableTracksViewportWidth());
        assertFalse(viewport.getScrollableTracksViewportHeight());
    }
}
