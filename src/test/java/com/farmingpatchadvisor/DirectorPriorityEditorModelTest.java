package com.farmingpatchadvisor;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorPriorityEditorModelTest
{
    @Test public void rankedListRoundTripsWithoutChangingStoredFormat()
    {
        List<Crop> catalog = DirectorPriorityEditorModel.catalog(t -> t == PatchType.HERB);
        List<Crop> ranked = DirectorPriorityEditorModel.selected(catalog, "Ranarr, Snapdragon, Ranarr, invalid");
        assertEquals(2, ranked.size());
        assertTrue(DirectorPriorityEditorModel.move(ranked, 1, -1));
        assertEquals("Snapdragon, Ranarr", DirectorPriorityEditorModel.encode(ranked));
        assertFalse(DirectorPriorityEditorModel.move(ranked, 0, -1));
    }

    @Test public void combinedCategoriesIncludeBothTypes()
    {
        List<Crop> crops = DirectorPriorityEditorModel.catalog(DirectorRunSelection.ALLOTMENTS::includes);
        assertTrue(crops.stream().anyMatch(c -> c.getPatchType() == PatchType.ALLOTMENT));
        assertTrue(crops.stream().anyMatch(c -> c.getPatchType() == PatchType.FLOWER));
    }

    @Test public void reservesRoundTripAndDropZeros()
    {
        assertEquals("ranarr=12", DirectorPriorityEditorModel.encodeReserves(
            DirectorPlanner.parseReserves("Ranarr=12, Guam=0")));
    }
}
