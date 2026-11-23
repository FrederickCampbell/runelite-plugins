package com.farminghelper;

import com.farminghelper.queststate.CropState;
import org.junit.Test;
import static org.junit.Assert.*;

public class StepEngineTest
{
    @Test
    public void herbTransitionsFollowQuestHelper()
    {
        assertEquals(StepEngine.Action.HARVEST,
            StepEngine.action(PatchType.HERB, CropState.HARVESTABLE, false, null));
        assertEquals(StepEngine.Action.PLANT,
            StepEngine.action(PatchType.HERB, CropState.EMPTY, false, null));
        assertEquals(StepEngine.Action.CURE,
            StepEngine.action(PatchType.HERB, CropState.DISEASED, false, null));
        assertEquals(StepEngine.Action.CLEAR_DEAD,
            StepEngine.action(PatchType.HERB, CropState.DEAD, false, null));
        assertEquals(StepEngine.Action.INSPECT,
            StepEngine.action(PatchType.HERB, null, false, null));
    }

    @Test
    public void treeTransitionsFollowQuestHelper()
    {
        assertEquals(StepEngine.Action.CHECK_HEALTH,
            StepEngine.action(PatchType.TREE, CropState.UNCHECKED, false, null));
        assertEquals(StepEngine.Action.REMOVE_TREE,
            StepEngine.action(PatchType.TREE, CropState.HARVESTABLE, false, null));
        assertEquals(StepEngine.Action.DIG_STUMP,
            StepEngine.action(PatchType.TREE, CropState.STUMP, false, null));
        assertEquals(StepEngine.Action.PLANT,
            StepEngine.action(PatchType.TREE, CropState.EMPTY, true, null));
    }

    @Test
    public void protectionHoldsNextRouteUntilConfirmed()
    {
        assertEquals(StepEngine.Action.PROTECT_TREE,
            StepEngine.action(PatchType.TREE, CropState.GROWING, true, false));
        assertEquals(StepEngine.Action.PROTECT_TREE,
            StepEngine.action(PatchType.TREE, CropState.GROWING, true, null));
        assertEquals(StepEngine.Action.COMPLETE,
            StepEngine.action(PatchType.TREE, CropState.GROWING, true, true));
        assertEquals(StepEngine.Action.COMPLETE,
            StepEngine.action(PatchType.TREE, CropState.GROWING, false, null));
    }

    @Test
    public void neverAutoAdvanceUnknownOrUnconfirmedState()
    {
        assertFalse(StepEngine.canAutoAdvance(
            StepEngine.Action.INSPECT, false, true, false));
        assertFalse(StepEngine.canAutoAdvance(
            StepEngine.Action.COMPLETE, true, false, false));
        assertTrue(StepEngine.canAutoAdvance(
            StepEngine.Action.COMPLETE, true, true, false));
        assertTrue(StepEngine.canAutoAdvance(
            StepEngine.Action.COMPLETE, true, false, true));
    }

    @Test
    public void harvestOnlyRequiresObservedWorkAndLiveEmptyState()
    {
        assertFalse(StepEngine.canFinishHarvestOnly(
            true, false, CropState.EMPTY, true));
        assertFalse(StepEngine.canFinishHarvestOnly(
            true, true, CropState.EMPTY, false));
        assertFalse(StepEngine.canFinishHarvestOnly(
            false, true, CropState.EMPTY, true));
        assertFalse(StepEngine.canFinishHarvestOnly(
            true, true, CropState.STUMP, true));
        assertTrue(StepEngine.canFinishHarvestOnly(
            true, true, CropState.EMPTY, true));
    }
}
