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
    public void guildWingAccessIsSeparateFromCropLevel()
    {
        assertEquals(45, DirectorWaypoints.guildAccessLevel(PatchType.BUSH));
        assertEquals(45, DirectorWaypoints.guildAccessLevel(PatchType.CACTUS));
        assertEquals(65, DirectorWaypoints.guildAccessLevel(PatchType.ANIMA));
        assertEquals(85, DirectorWaypoints.guildAccessLevel(PatchType.SPIRIT_TREE));
        assertEquals(85, DirectorWaypoints.guildAccessLevel(PatchType.CELASTRUS));
        assertEquals(85, DirectorWaypoints.guildAccessLevel(PatchType.REDWOOD));
    }

    @Test
    public void missingAccountStateIsNotAnUnlock()
    {
        assertEquals(DirectorWaypoints.AccessState.UNKNOWN,
            DirectorWaypoints.access(null, PatchType.TREE, "Auburnvale", 99).state);
        assertEquals(DirectorWaypoints.AccessState.UNKNOWN,
            DirectorWaypoints.access(null, PatchType.FRUIT_TREE, "Lletya", 99).state);
        assertNotNull(DirectorWaypoints.access(null, PatchType.HERB,
            "Harmony Island", 99).issue(false));
        assertNull(DirectorWaypoints.access(null, PatchType.HERB,
            "Harmony Island", 99).issue(true));
    }

    @Test
    public void standardWaypointsHaveCoordinates()
    {
        assertNotNull(DirectorWaypoints.point(PatchType.HERB, "Falador"));
        assertNotNull(DirectorWaypoints.point(PatchType.TREE, "Auburnvale"));
        assertNull(DirectorWaypoints.point(PatchType.TREE, "Catherby"));
    }
}
