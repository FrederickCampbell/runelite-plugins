package com.farminghelper;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompostGuidanceTest
{
    @Test public void newPlantingWaitsForCompostBeforeCompletingOrPaying()
    {
        RunPlanner.Step step = new RunPlanner.Step(
            new FarmRunPatch("Falador", "", PatchType.HERB),
            CropCatalog.findByName(PatchType.HERB, "Ranarr"));
        assertEquals(StepEngine.Action.COMPOST, StepEngine.withCompost(
            StepEngine.Action.COMPLETE, step, true, true, false));
        assertEquals(StepEngine.Action.COMPLETE, StepEngine.withCompost(
            StepEngine.Action.COMPLETE, step, true, true, true));
        assertEquals(StepEngine.Action.COMPLETE, StepEngine.withCompost(
            StepEngine.Action.COMPLETE, step, false, true, false));
    }

    @Test public void existingCropsDoNotRequireFreshCompost()
    {
        RunPlanner.Step step = new RunPlanner.Step(
            new FarmRunPatch("Falador", "", PatchType.TREE), null, false);
        assertEquals(StepEngine.Action.COMPLETE, StepEngine.withCompost(
            StepEngine.Action.COMPLETE, step, true, false, false));
    }
}
