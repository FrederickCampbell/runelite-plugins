package com.farmingpatchadvisor;

import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorWaypointExpansionTest
{
    @Test
    public void fruitCalquatAndHardwoodLocationsComeFromQuestHelper()
    {
        assertEquals(2476, DirectorWaypoints.exactPoint(
            new FarmRunPatch("Gnome Stronghold", "", PatchType.FRUIT_TREE)).getX());
        assertEquals(2795, DirectorWaypoints.exactPoint(
            new FarmRunPatch("Tai Bwo Wannai", "", PatchType.CALQUAT)).getX());
        assertEquals(3708, DirectorWaypoints.exactPoint(
            new FarmRunPatch("Fossil Island", "Middle", PatchType.HARDWOOD_TREE)).getX());
        assertEquals(3715, DirectorWaypoints.exactPoint(
            new FarmRunPatch("Fossil Island", "East", PatchType.HARDWOOD_TREE)).getX());
    }
}
