package com.farmingpatchadvisor;

import com.farmingpatchadvisor.queststate.CropState;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorStepEngineTest
{
    @Test
    public void herbTransitionsFollowQuestHelper()
    {
        assertEquals(DirectorStepEngine.Action.HARVEST,
            DirectorStepEngine.action(PatchType.HERB, CropState.HARVESTABLE, false, null));
        assertEquals(DirectorStepEngine.Action.PLANT,
            DirectorStepEngine.action(PatchType.HERB, CropState.EMPTY, false, null));
        assertEquals(DirectorStepEngine.Action.CURE,
            DirectorStepEngine.action(PatchType.HERB, CropState.DISEASED, false, null));
        assertEquals(DirectorStepEngine.Action.CLEAR_DEAD,
            DirectorStepEngine.action(PatchType.HERB, CropState.DEAD, false, null));
        assertEquals(DirectorStepEngine.Action.INSPECT,
            DirectorStepEngine.action(PatchType.HERB, null, false, null));
    }

    @Test
    public void treeTransitionsFollowQuestHelper()
    {
        assertEquals(DirectorStepEngine.Action.CHECK_HEALTH,
            DirectorStepEngine.action(PatchType.TREE, CropState.UNCHECKED, false, null));
        assertEquals(DirectorStepEngine.Action.REMOVE_TREE,
            DirectorStepEngine.action(PatchType.TREE, CropState.HARVESTABLE, false, null));
        assertEquals(DirectorStepEngine.Action.DIG_STUMP,
            DirectorStepEngine.action(PatchType.TREE, CropState.STUMP, false, null));
        assertEquals(DirectorStepEngine.Action.PLANT,
            DirectorStepEngine.action(PatchType.TREE, CropState.EMPTY, true, null));
    }

    @Test
    public void protectionHoldsNextRouteUntilConfirmed()
    {
        assertEquals(DirectorStepEngine.Action.PROTECT_TREE,
            DirectorStepEngine.action(PatchType.TREE, CropState.GROWING, true, false));
        assertEquals(DirectorStepEngine.Action.PROTECT_TREE,
            DirectorStepEngine.action(PatchType.TREE, CropState.GROWING, true, null));
        assertEquals(DirectorStepEngine.Action.COMPLETE,
            DirectorStepEngine.action(PatchType.TREE, CropState.GROWING, true, true));
        assertEquals(DirectorStepEngine.Action.COMPLETE,
            DirectorStepEngine.action(PatchType.TREE, CropState.GROWING, false, null));
    }

    @Test
    public void neverAutoAdvanceUnknownOrUnconfirmedState()
    {
        assertFalse(DirectorStepEngine.canAutoAdvance(
            DirectorStepEngine.Action.INSPECT, false, true, false));
        assertFalse(DirectorStepEngine.canAutoAdvance(
            DirectorStepEngine.Action.COMPLETE, true, false, false));
        assertTrue(DirectorStepEngine.canAutoAdvance(
            DirectorStepEngine.Action.COMPLETE, true, true, false));
        assertTrue(DirectorStepEngine.canAutoAdvance(
            DirectorStepEngine.Action.COMPLETE, true, false, true));
    }

    @Test
    public void harvestOnlyRequiresObservedWorkAndLiveEmptyState()
    {
        assertFalse(DirectorStepEngine.canFinishHarvestOnly(
            true, false, CropState.EMPTY, true));
        assertFalse(DirectorStepEngine.canFinishHarvestOnly(
            true, true, CropState.EMPTY, false));
        assertFalse(DirectorStepEngine.canFinishHarvestOnly(
            false, true, CropState.EMPTY, true));
        assertFalse(DirectorStepEngine.canFinishHarvestOnly(
            true, true, CropState.STUMP, true));
        assertTrue(DirectorStepEngine.canFinishHarvestOnly(
            true, true, CropState.EMPTY, true));
    }
}
