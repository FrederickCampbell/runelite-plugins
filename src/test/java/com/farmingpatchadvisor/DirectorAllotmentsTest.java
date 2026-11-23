package com.farmingpatchadvisor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorAllotmentsTest
{
    @Test
    public void twoAllotmentsHaveIndependentSeedAllocationAndStepNames()
    {
        FarmRunPatch pair = new FarmRunPatch("Falador", "Allotments",
            PatchType.ALLOTMENT, 2);
        List<FarmRunPatch> steps = DirectorAllotments.split(pair,
            Arrays.asList("North West", "South East"));
        assertEquals(2, steps.size());
        assertEquals(1, steps.get(0).getPatchCount());
        assertEquals("North West", steps.get(0).getName());
        assertEquals("South East", steps.get(1).getName());
    }

    @Test
    public void neverInventMissingPatchDefinitions()
    {
        assertTrue(DirectorAllotments.split(
            new FarmRunPatch("Falador", "Allotments", PatchType.ALLOTMENT, 2),
            Collections.singletonList("North West")).isEmpty());
    }
}
