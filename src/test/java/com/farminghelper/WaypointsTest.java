package com.farminghelper;

import org.junit.Test;
import static org.junit.Assert.*;

public class WaypointsTest
{
    @Test
    public void farmingGuildRequires65ForBothPatches()
    {
        assertTrue(Waypoints.accessIssue(null, PatchType.TREE,
            "Farming Guild", 64, false).contains("65"));
        assertNull(Waypoints.accessIssue(null, PatchType.TREE,
            "Farming Guild", 65, false));
        assertNull(Waypoints.accessIssue(null, PatchType.HERB,
            "Farming Guild", 65, false));
    }

    @Test
    public void unverifiedLocationsAreConservative()
    {
        assertNotNull(Waypoints.accessIssue(null, PatchType.HERB,
            "Harmony Island", 99, false));
        assertNull(Waypoints.accessIssue(null, PatchType.HERB,
            "Harmony Island", 99, true));
    }

    @Test
    public void guildWingAccessIsSeparateFromCropLevel()
    {
        assertEquals(45, Waypoints.guildAccessLevel(PatchType.BUSH));
        assertEquals(45, Waypoints.guildAccessLevel(PatchType.CACTUS));
        assertEquals(65, Waypoints.guildAccessLevel(PatchType.ANIMA));
        assertEquals(85, Waypoints.guildAccessLevel(PatchType.SPIRIT_TREE));
        assertEquals(85, Waypoints.guildAccessLevel(PatchType.CELASTRUS));
        assertEquals(85, Waypoints.guildAccessLevel(PatchType.REDWOOD));
    }

    @Test
    public void missingAccountStateIsNotAnUnlock()
    {
        assertEquals(Waypoints.AccessState.UNKNOWN,
            Waypoints.access(null, PatchType.TREE, "Auburnvale", 99).state);
        assertEquals(Waypoints.AccessState.UNKNOWN,
            Waypoints.access(null, PatchType.FRUIT_TREE, "Lletya", 99).state);
        assertNotNull(Waypoints.access(null, PatchType.HERB,
            "Harmony Island", 99).issue(false));
        assertNull(Waypoints.access(null, PatchType.HERB,
            "Harmony Island", 99).issue(true));
    }

    @Test
    public void standardWaypointsHaveCoordinates()
    {
        assertNotNull(Waypoints.point(PatchType.HERB, "Falador"));
        assertNotNull(Waypoints.point(PatchType.TREE, "Auburnvale"));
        assertNull(Waypoints.point(PatchType.TREE, "Catherby"));
    }
}
