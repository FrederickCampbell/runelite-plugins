package com.farmingpatchadvisor;

import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.gameval.ItemID;

public class DirectorWidgetOverlayTest
{
    @Test public void dialogueMatchesOnlyTheCurrentInteraction()
    {
        assertTrue(DirectorWidgetOverlay.matchesChoice(DirectorStepEngine.Action.PAY_REMOVE, "Would you chop my tree down for me?"));
        assertTrue(DirectorWidgetOverlay.matchesChoice(DirectorStepEngine.Action.PROTECT_TREE, "Would you look after my crops for me?"));
        assertFalse(DirectorWidgetOverlay.matchesChoice(DirectorStepEngine.Action.PAY_REMOVE, "Yes."));
        assertFalse(DirectorWidgetOverlay.matchesChoice(DirectorStepEngine.Action.PROTECT_TREE, "Would you chop my tree down for me?"));
    }
    @Test public void travelHighlightsSelectedItemNotPlantingStock()
    {
        DirectorPlanner.Step step = new DirectorPlanner.Step(new FarmRunPatch("Lumbridge", "", PatchType.TREE), null);
        DirectorWorldGuide.Target target = new DirectorWorldGuide.Target(step, DirectorStepEngine.Action.INSPECT,
            "test", false, ItemID.BUCKET_ULTRACOMPOST, RouteTeleport.LUMBRIDGE, true);
        assertTrue(DirectorWorldGuide.matchesInventoryItem(target, ItemID.POH_TABLET_LUMBRIDGETELEPORT));
        assertFalse(DirectorWorldGuide.matchesInventoryItem(target, ItemID.SPADE));
        assertEquals("Lumbridge Teleport", DirectorWorldGuide.spellName(target));
    }
}
