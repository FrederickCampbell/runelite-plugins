package com.farminghelper;

import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.gameval.ItemID;

public class WidgetOverlayTest
{
    @Test public void dialogueMatchesOnlyTheCurrentInteraction()
    {
        assertTrue(WidgetOverlay.matchesChoice(StepEngine.Action.PAY_REMOVE, "Would you chop my tree down for me?"));
        assertTrue(WidgetOverlay.matchesChoice(StepEngine.Action.PROTECT_TREE, "Would you look after my crops for me?"));
        assertFalse(WidgetOverlay.matchesChoice(StepEngine.Action.PAY_REMOVE, "Yes."));
        assertFalse(WidgetOverlay.matchesChoice(StepEngine.Action.PROTECT_TREE, "Would you chop my tree down for me?"));
    }
    @Test public void travelHighlightsSelectedItemNotPlantingStock()
    {
        RunPlanner.Step step = new RunPlanner.Step(new FarmRunPatch("Lumbridge", "", PatchType.TREE), null);
        WorldGuide.Target target = new WorldGuide.Target(step, StepEngine.Action.INSPECT,
            "test", false, ItemID.BUCKET_ULTRACOMPOST, RouteTeleport.LUMBRIDGE, true);
        assertTrue(WorldGuide.matchesInventoryItem(target, ItemID.POH_TABLET_LUMBRIDGETELEPORT));
        assertFalse(WorldGuide.matchesInventoryItem(target, ItemID.SPADE));
        assertEquals("Lumbridge Teleport", WorldGuide.spellName(target));
    }
}
