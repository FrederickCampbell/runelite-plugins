package com.farmingpatchadvisor;

import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorWorldGuideTest
{
    @Test public void matchingPairRequiresBothKnownPositions()
    {
        FarmRunPatch north = new FarmRunPatch("Falador", "North West", PatchType.ALLOTMENT);
        WorldPoint nw = new WorldPoint(3050, 3315, 0), se = new WorldPoint(3056, 3309, 0);
        assertEquals(1, DirectorWorldGuide.select(north, Arrays.asList(se, nw), 2, null));
        assertEquals(-1, DirectorWorldGuide.select(north, Collections.singletonList(se), 2, null));
    }

    @Test public void exactCoordinatesAreNotAllowedToSelectDistantOrTiedObjects()
    {
        FarmRunPatch tree = new FarmRunPatch("Fossil Island", "East", PatchType.HARDWOOD_TREE);
        WorldPoint exact = new WorldPoint(3715, 3835, 0);
        assertEquals(1, DirectorWorldGuide.select(tree, Arrays.asList(new WorldPoint(3702,3837,0), exact), 3, exact));
        assertEquals(-1, DirectorWorldGuide.select(tree, Collections.singletonList(new WorldPoint(3702,3837,0)), 3, exact));
        assertEquals(-1, DirectorWorldGuide.select(tree, Arrays.asList(new WorldPoint(3714,3835,0), new WorldPoint(3716,3835,0)), 3, exact));
    }

    @Test public void itemHighlightUsesThePlannedCompostBucket()
    {
        DirectorPlanner.Step step = new DirectorPlanner.Step(new FarmRunPatch("Falador", "", PatchType.HERB),
            CropCatalog.findByName(PatchType.HERB, "Ranarr"));
        int bucket = net.runelite.api.gameval.ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED;
        DirectorWorldGuide.Target target = new DirectorWorldGuide.Target(step, DirectorStepEngine.Action.COMPOST,
            "test", true, bucket);
        assertEquals(bucket, DirectorWorldGuide.itemId(target));
        assertTrue(DirectorWorldGuide.matchesInventoryItem(target, bucket));
        assertFalse(DirectorWorldGuide.matchesInventoryItem(target, step.crop.getItemId()));
    }

    @Test public void numberedGrapevinesDoNotGuessIdentity()
    {
        FarmRunPatch vine = new FarmRunPatch("Kourend", "East 1", PatchType.GRAPEVINE);
        assertEquals(-1, DirectorWorldGuide.select(vine, Arrays.asList(new WorldPoint(1,1,0), new WorldPoint(2,2,0)), 12, null));
    }
}
