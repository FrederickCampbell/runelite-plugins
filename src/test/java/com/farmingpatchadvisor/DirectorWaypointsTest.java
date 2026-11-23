package com.farmingpatchadvisor;

import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorWaypointsTest
{
    @Test
    public void farmingGuildRequires65ForBothPatches()
    {
        assertTrue(DirectorWaypoints.accessIssue(null, PatchType.TREE,
            "Farming Guild", 64, false).contains("65"));
        assertNull(DirectorWaypoints.accessIssue(null, PatchType.TREE,
            "Farming Guild", 65, false));
        assertNull(DirectorWaypoints.accessIssue(null, PatchType.HERB,
            "Farming Guild", 65, false));
    }

    @Test
    public void unverifiedLocationsAreConservative()
    {
        assertNotNull(DirectorWaypoints.accessIssue(null, PatchType.HERB,
            "Harmony Island", 99, false));
        assertNull(DirectorWaypoints.accessIssue(null, PatchType.HERB,
            "Harmony Island", 99, true));
    }

    @Test
    public void standardWaypointsHaveCoordinates()
    {
        assertNotNull(DirectorWaypoints.point(PatchType.HERB, "Falador"));
        assertNotNull(DirectorWaypoints.point(PatchType.TREE, "Auburnvale"));
        assertNull(DirectorWaypoints.point(PatchType.TREE, "Catherby"));
    }
}
