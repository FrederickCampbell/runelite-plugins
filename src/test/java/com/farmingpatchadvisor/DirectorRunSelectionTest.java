package com.farmingpatchadvisor;

import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorRunSelectionTest
{
    @Test
    public void allRunIncludesEveryAdvisorCropType()
    {
        for (PatchType type : PatchType.values())
        {
            assertTrue(DirectorRunSelection.ALL.includes(type));
        }
        assertTrue(DirectorRunSelection.FRUIT_TREES.includes(PatchType.CALQUAT));
        assertFalse(DirectorRunSelection.FRUIT_TREES.includes(PatchType.TREE));
    }

    @Test
    public void separateRunTypesUseExistingAdvisorRouteFilters()
    {
        assertEquals(FarmRunFilter.HERB, DirectorRunSelection.HERBS.filter);
        assertEquals(FarmRunFilter.ALL, DirectorRunSelection.CORE.filter);
        assertTrue(DirectorRunSelection.SPECIAL.includes(PatchType.SEAWEED));
    }
}
