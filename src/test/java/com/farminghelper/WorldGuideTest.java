package com.farminghelper;

import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldGuideTest
{
    @Test public void matchingPairRequiresBothKnownPositions()
    {
        FarmRunPatch north = new FarmRunPatch("Falador", "North West", PatchType.ALLOTMENT);
        WorldPoint nw = new WorldPoint(3050, 3315, 0), se = new WorldPoint(3056, 3309, 0);
        assertEquals(1, WorldGuide.select(north, Arrays.asList(se, nw), 2, null));
        assertEquals(-1, WorldGuide.select(north, Collections.singletonList(se), 2, null));
    }

    @Test public void exactCoordinatesAreNotAllowedToSelectDistantOrTiedObjects()
    {
        FarmRunPatch tree = new FarmRunPatch("Fossil Island", "East", PatchType.HARDWOOD_TREE);
        WorldPoint exact = new WorldPoint(3715, 3835, 0);
        assertEquals(1, WorldGuide.select(tree, Arrays.asList(new WorldPoint(3702,3837,0), exact), 3, exact));
        assertEquals(-1, WorldGuide.select(tree, Collections.singletonList(new WorldPoint(3702,3837,0)), 3, exact));
        assertEquals(-1, WorldGuide.select(tree, Arrays.asList(new WorldPoint(3714,3835,0), new WorldPoint(3716,3835,0)), 3, exact));
    }

    @Test public void itemHighlightUsesThePlannedCompostBucket()
    {
        RunPlanner.Step step = new RunPlanner.Step(new FarmRunPatch("Falador", "", PatchType.HERB),
            CropCatalog.findByName(PatchType.HERB, "Ranarr"));
        int bucket = net.runelite.api.gameval.ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED;
        WorldGuide.Target target = new WorldGuide.Target(step, StepEngine.Action.COMPOST,
            "test", true, bucket);
        assertEquals(bucket, WorldGuide.itemId(target));
        assertTrue(WorldGuide.matchesInventoryItem(target, bucket));
        assertFalse(WorldGuide.matchesInventoryItem(target, step.crop.getItemId()));
    }

    @Test public void numberedGrapevinesDoNotGuessIdentity()
    {
        FarmRunPatch vine = new FarmRunPatch("Kourend", "East 1", PatchType.GRAPEVINE);
        assertEquals(-1, WorldGuide.select(vine, Arrays.asList(new WorldPoint(1,1,0), new WorldPoint(2,2,0)), 12, null));
    }
}
