package com.farminghelper;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PriorityEditorModelTest
{
    @Test public void rankedListRoundTripsWithoutChangingStoredFormat()
    {
        List<Crop> catalog = PriorityEditorModel.catalog(t -> t == PatchType.HERB);
        List<Crop> ranked = PriorityEditorModel.selected(catalog, "Ranarr, Snapdragon, Ranarr, invalid");
        assertEquals(2, ranked.size());
        assertTrue(PriorityEditorModel.move(ranked, 1, -1));
        assertEquals("Snapdragon, Ranarr", PriorityEditorModel.encode(ranked));
        assertFalse(PriorityEditorModel.move(ranked, 0, -1));
    }

    @Test public void combinedCategoriesIncludeBothTypes()
    {
        List<Crop> crops = PriorityEditorModel.catalog(RunSelection.ALLOTMENTS::includes);
        assertTrue(crops.stream().anyMatch(c -> c.getPatchType() == PatchType.ALLOTMENT));
        assertTrue(crops.stream().anyMatch(c -> c.getPatchType() == PatchType.FLOWER));
    }

    @Test public void reservesRoundTripAndDropZeros()
    {
        assertEquals("ranarr=12", PriorityEditorModel.encodeReserves(
            RunPlanner.parseReserves("Ranarr=12, Guam=0")));
    }
}
