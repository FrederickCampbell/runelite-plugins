package com.farminghelper;

import org.junit.Test;
import static org.junit.Assert.*;

public class WaypointExpansionTest
{
    @Test
    public void bushAndSpecialtyPatchesAreNeverMisroutedToHerbAreas()
    {
        assertNull(Waypoints.exactPoint(
            new FarmRunPatch("Ardougne", "", PatchType.BUSH)));
        assertNull(Waypoints.exactPoint(
            new FarmRunPatch("Morytania", "", PatchType.MUSHROOM)));
        assertNotNull(Waypoints.exactPoint(
            new FarmRunPatch("Ardougne", "", PatchType.FLOWER)));
    }

    @Test
    public void fruitCalquatAndHardwoodLocationsComeFromQuestHelper()
    {
        assertEquals(2476, Waypoints.exactPoint(
            new FarmRunPatch("Gnome Stronghold", "", PatchType.FRUIT_TREE)).getX());
        assertEquals(2795, Waypoints.exactPoint(
            new FarmRunPatch("Tai Bwo Wannai", "", PatchType.CALQUAT)).getX());
        assertEquals(3708, Waypoints.exactPoint(
            new FarmRunPatch("Fossil Island", "Middle", PatchType.HARDWOOD_TREE)).getX());
        assertEquals(3715, Waypoints.exactPoint(
            new FarmRunPatch("Fossil Island", "East", PatchType.HARDWOOD_TREE)).getX());
    }
}
