package com.farmingpatchadvisor;

import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorCompostGuidanceTest
{
    @Test public void newPlantingWaitsForCompostBeforeCompletingOrPaying()
    {
        DirectorPlanner.Step step = new DirectorPlanner.Step(
            new FarmRunPatch("Falador", "", PatchType.HERB),
            CropCatalog.findByName(PatchType.HERB, "Ranarr"));
        assertEquals(DirectorStepEngine.Action.COMPOST, DirectorStepEngine.withCompost(
            DirectorStepEngine.Action.COMPLETE, step, true, true, false));
        assertEquals(DirectorStepEngine.Action.COMPLETE, DirectorStepEngine.withCompost(
            DirectorStepEngine.Action.COMPLETE, step, true, true, true));
        assertEquals(DirectorStepEngine.Action.COMPLETE, DirectorStepEngine.withCompost(
            DirectorStepEngine.Action.COMPLETE, step, false, true, false));
    }

    @Test public void existingCropsDoNotRequireFreshCompost()
    {
        DirectorPlanner.Step step = new DirectorPlanner.Step(
            new FarmRunPatch("Falador", "", PatchType.TREE), null, false);
        assertEquals(DirectorStepEngine.Action.COMPLETE, DirectorStepEngine.withCompost(
            DirectorStepEngine.Action.COMPLETE, step, true, false, false));
    }
}
