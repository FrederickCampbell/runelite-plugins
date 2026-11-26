package com.farminghelper;
import java.util.*;
import net.runelite.api.gameval.ItemID;
import com.farminghelper.queststate.CropState;
import org.junit.Test;
import static org.junit.Assert.*;
public class StepItemsTest
{
    @Test public void onlyCurrentActionAndFirstMatchingSlot()
    {
        Crop palm=CropCatalog.findByName(PatchType.FRUIT_TREE,"Palm sapling");
        int id=palm.getItemId();
        assertEquals(Set.of(id),StepItems.select(StepEngine.Action.PLANT,PatchType.FRUIT_TREE,palm,ItemID.BUCKET_ULTRACOMPOST,1351,Map.of()));
        assertEquals(Set.of(ItemID.BUCKET_ULTRACOMPOST),StepItems.select(StepEngine.Action.COMPOST,PatchType.FRUIT_TREE,palm,ItemID.BUCKET_ULTRACOMPOST,1351,Map.of()));
        assertTrue(StepItems.select(StepEngine.Action.CHECK_HEALTH,PatchType.FRUIT_TREE,palm,ItemID.BUCKET_ULTRACOMPOST,1351,Map.of()).isEmpty());
        net.runelite.api.ItemContainer inventory=org.mockito.Mockito.mock(net.runelite.api.ItemContainer.class);
        org.mockito.Mockito.when(inventory.find(id)).thenReturn(1);
        assertTrue(StepItems.firstSlot(id,1,inventory));
        assertFalse(StepItems.firstSlot(id,2,inventory));
        assertFalse(StepItems.firstSlot(id,-1,inventory));
        assertFalse(StepItems.firstSlot(id,1,null));
        org.mockito.Mockito.when(inventory.find(id)).thenReturn(-1);
        assertFalse(StepItems.firstSlot(id,-1,inventory));
        org.mockito.Mockito.verify(inventory,org.mockito.Mockito.never()).getItems();
    }
    @Test public void clearOnlyNeedsAnOccupiedActionablePatch()
    {
        assertTrue(ClearOnly.eligible(CropState.UNCHECKED));
        assertTrue(ClearOnly.eligible(CropState.HARVESTABLE));
        assertTrue(ClearOnly.eligible(CropState.DEAD));
        assertTrue(ClearOnly.eligible(CropState.STUMP));
        assertFalse(ClearOnly.eligible(CropState.EMPTY));
        assertFalse(ClearOnly.eligible(CropState.GROWING));
        assertFalse(ClearOnly.eligible(null));
    }
}
