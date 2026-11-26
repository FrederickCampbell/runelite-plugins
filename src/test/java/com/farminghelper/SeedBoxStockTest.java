package com.farminghelper;

import java.util.*;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SeedBoxStockTest
{
    private static final int RANARR=ItemID.RANARR_SEED;
    private static final int SNAP=ItemID.SNAPDRAGON_SEED;
    @Test public void orderedNativeContentsAndRemoval()
    {
        SeedBoxStock box=new SeedBoxStock();
        ItemContainer container=mock(ItemContainer.class);
        when(container.getItems()).thenReturn(new Item[]{new Item(SNAP,4),new Item(RANARR,7)});
        assertFalse(box.known());
        box.observe(container);
        Map<Integer,Integer> contents=box.carried(Map.of(ItemID.SEED_BOX,1));
        assertTrue(box.known());assertEquals(List.of(SNAP,RANARR),new ArrayList<>(contents.keySet()));
        assertFalse(SeedBoxStock.direct(CropCatalog.findByItemId(RANARR),contents));
        assertTrue(SeedBoxStock.direct(CropCatalog.findByItemId(SNAP),contents));
        assertEquals(contents,box.carried(Map.of(ItemID.SEED_BOX_OPEN,1)));
        assertTrue(box.carried(Map.of()).isEmpty());assertFalse(box.known());
    }
    @Test public void countedAsCarriedNotLooseOrBank()
    {
        Map<Integer,Integer> bag=Map.of(ItemID.SEED_BOX_OPEN,1,RANARR,2);
        PhysicalStock s=new PhysicalStock(bag,Map.of(),Map.of(RANARR,5),Map.of(),Map.of(RANARR,7),id->"",id->id);
        assertEquals(2,(int)s.bag.get(RANARR));assertEquals(9,(int)s.carried.get(RANARR));
        assertEquals(14,(int)s.allocation.get(RANARR));assertEquals(5,(int)s.storage.get(RANARR));
        assertEquals(7,(int)s.shelf.get(RANARR));assertTrue(GuideView.missingItems(Map.of(RANARR,9),s.carried).isEmpty());
        assertEquals(1,SeedBoxStock.slots(Map.of(RANARR,7),Map.of(),s.seedBox,id->true));
        PhysicalStock absent=new PhysicalStock(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(RANARR,7),id->"",id->id);
        assertFalse(absent.carried.containsKey(RANARR));
    }
    @Test public void transferUpdatesTotalWithoutDoubleCounting()
    {
        SeedBoxStock box=new SeedBoxStock();ItemContainer container=mock(ItemContainer.class);
        when(container.getItems()).thenReturn(new Item[]{new Item(RANARR,7)},new Item[]{new Item(RANARR,4)});
        box.observe(container);Map<Integer,Integer> bag=Map.of(ItemID.SEED_BOX,1,RANARR,2);
        assertEquals(9,(int)PhysicalStock.combine(bag,box.carried(bag)).get(RANARR));
        box.observe(container);bag=Map.of(ItemID.SEED_BOX,1,RANARR,5);
        assertEquals(9,(int)PhysicalStock.combine(bag,box.carried(bag)).get(RANARR));
        box.clear();assertTrue(box.carried(bag).isEmpty());
    }
}
