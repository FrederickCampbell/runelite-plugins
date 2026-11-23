package com.farminghelper;

import org.junit.Test;
import static org.junit.Assert.*;

public class RunNavigationTest
{
    @Test public void editingAnotherRunDoesNotChangeInProgressMode()
    {
        assertEquals(RunSelection.HERBS,
            FarmingHelperPanel.replanSelection(RunSelection.HERBS,
                RunSelection.MERGED));
        assertEquals(RunSelection.MERGED,
            FarmingHelperPanel.replanSelection(null, RunSelection.MERGED));
        assertTrue(RunSelection.ALLOTMENTS.includes(PatchType.ALLOTMENT));
        assertTrue(RunSelection.ALLOTMENTS.includes(PatchType.FLOWER));
    }
}
