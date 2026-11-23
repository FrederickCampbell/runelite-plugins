package com.farminghelper;

import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;
import org.junit.Test;
import static org.junit.Assert.*;

public class RunReadinessTest
{
    @Test
    public void skipsLiveAndPredictedGrowingEvenWhenProtectionIsUnknown()
    {
        assertTrue(RunReadiness.growing(new Observation(CropState.GROWING, Source.LIVE, 1L, null, "")));
        assertTrue(RunReadiness.growing(new Observation(CropState.GROWING, Source.TIME_TRACKING, 1L, false, "")));
    }

    @Test
    public void unknownAndActionableStatesAreNotGrowing()
    {
        assertFalse(RunReadiness.growing(new Observation(null, Source.NONE, 0L, null, "")));
        for (CropState state : new CropState[]{CropState.EMPTY, CropState.HARVESTABLE, CropState.UNCHECKED,
            CropState.DEAD, CropState.DISEASED, CropState.STUMP})
            assertFalse(RunReadiness.growing(new Observation(state, Source.LIVE, 1L, null, "")));
    }

    @Test
    public void waitMessageRequiresAtLeastOneEligiblePatchAndAllGrowing()
    {
        assertTrue(RunReadiness.waitingForGrowth(5, 5));
        assertFalse(RunReadiness.waitingForGrowth(0, 0));
        assertFalse(RunReadiness.waitingForGrowth(4, 5));
        assertFalse(RunReadiness.waitingForGrowth(0, 5));
    }

    @Test
    public void farmingLevelGateUsesLowestAvailableCropNotPreferredCrop()
    {
        assertFalse(RunReadiness.levelEligible(PatchType.HERB, 1));
        assertTrue(RunReadiness.levelEligible(PatchType.HERB, 9));
        assertFalse(RunReadiness.levelEligible(PatchType.TREE, 1));
        assertTrue(RunReadiness.levelEligible(PatchType.TREE, 15));
    }
}
