package com.farminghelper;

import org.junit.Test;
import static org.junit.Assert.*;

public class RunSelectionTest
{
    @Test
    public void allRunIncludesEveryAdvisorCropType()
    {
        for (PatchType type : PatchType.values())
        {
            assertTrue(RunSelection.ALL.includes(type));
        }
        assertTrue(RunSelection.FRUIT_TREES.includes(PatchType.CALQUAT));
        assertFalse(RunSelection.FRUIT_TREES.includes(PatchType.TREE));
    }

    @Test
    public void separateRunTypesUseExistingAdvisorRouteFilters()
    {
        assertEquals(FarmRunFilter.HERB, RunSelection.HERBS.filter);
        assertEquals(FarmRunFilter.ALL, RunSelection.CORE.filter);
        assertTrue(RunSelection.SPECIAL.includes(PatchType.SEAWEED));
    }
}
