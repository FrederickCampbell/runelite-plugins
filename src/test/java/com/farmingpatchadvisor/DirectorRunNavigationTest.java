package com.farmingpatchadvisor;

import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorRunNavigationTest
{
    @Test public void editingAnotherRunDoesNotChangeInProgressMode()
    {
        assertEquals(DirectorRunSelection.HERBS,
            FarmingDirectorPanel.replanSelection(DirectorRunSelection.HERBS,
                DirectorRunSelection.MERGED));
        assertEquals(DirectorRunSelection.MERGED,
            FarmingDirectorPanel.replanSelection(null, DirectorRunSelection.MERGED));
        assertTrue(DirectorRunSelection.ALLOTMENTS.includes(PatchType.ALLOTMENT));
        assertTrue(DirectorRunSelection.ALLOTMENTS.includes(PatchType.FLOWER));
    }
}
