package com.farminghelper;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class NearestRouteTest
{
    @Test
    public void proximityOrderingKeepsEveryStopAndHonorsStartPoint()
    {
        FarmRunPatch far = new FarmRunPatch("Far", "", PatchType.HERB);
        FarmRunPatch near = new FarmRunPatch("Near", "", PatchType.HERB);
        List<FarmRunPatch> ordered = NearestRoute.order(
            Arrays.asList(far, near), new WorldPoint(100, 100, 0),
            p -> p == near ? new WorldPoint(103, 104, 0)
                : new WorldPoint(700, 800, 0));
        assertSame(near, ordered.get(0));
        assertSame(far, ordered.get(1));
    }

    @Test
    public void unknownCoordinatesRemainLastWhenKnownCoordinatesExist()
    {
        FarmRunPatch unknown = new FarmRunPatch("No data", "", PatchType.HERB);
        FarmRunPatch known = new FarmRunPatch("Near", "", PatchType.HERB);
        assertSame(known, NearestRoute.order(
            Arrays.asList(unknown, known), new WorldPoint(100, 100, 0),
            p -> p == unknown ? null : new WorldPoint(101, 101, 0)).get(0));
    }
}
