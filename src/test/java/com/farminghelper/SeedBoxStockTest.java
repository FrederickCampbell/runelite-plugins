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
    @Test public void hypotheticalPackingNeverInventsCapacityOrStock()
    {
        var required=Map.of(1,8,2,6,3,4,4,1,5,1,6,1,7,1);
        var carried=Map.of(1,8,2,6,3,4);
        var storage=Map.of(4,1,5,1,6,1,7,1);
        var plan=SeedBoxStock.packing(required,carried,storage,Map.of(),true,id->true);
        assertEquals(6,plan.size());
        assertFalse(plan.containsKey(7));
        assertTrue(SeedBoxStock.packing(required,carried,storage,Map.of(),false,id->true).isEmpty());
        assertFalse(SeedBoxStock.packing(required,carried,Map.of(),Map.of(),true,id->true).containsKey(4));
        assertEquals(1,Preparation.packedSlots(4,carried,Map.of(1,8,2,6,3,4),Map.of(),Map.of(),plan,id->true));
        assertEquals(6,SeedBoxStock.packing(Map.of(1,100),Map.of(1,1),Map.of(),
            Map.of(2,1,3,1,4,1,5,1,6,1,7,1),true,id->true).size());
    }
    @Test public void bankedSeedBoxIsRequiredOnlyForObtainableEligibleTypes()
    {
        int box=ItemID.SEED_BOX,open=ItemID.SEED_BOX_OPEN;
        var required=Map.of(RANARR,3,SNAP,4);
        var bank=Map.of(box,1,RANARR,3,SNAP,4);
        assertEquals(box,SeedBoxStock.requiredBox(required,Map.of(),bank,bank,Map.of(),false,id->true));
        assertEquals(-1,SeedBoxStock.requiredBox(required,Map.of(),bank,bank,Map.of(),false,id->id==RANARR));
        assertEquals(-1,SeedBoxStock.requiredBox(required,Map.of(),Map.of(),bank,Map.of(),false,id->true));
        assertEquals(-1,SeedBoxStock.requiredBox(required,Map.of(),bank,Map.of(RANARR,3),Map.of(),false,id->true));
        assertEquals(open,SeedBoxStock.requiredBox(Map.of(RANARR,3),Map.of(open,1),bank,bank,Map.of(RANARR,3),true,id->true));
        assertEquals(3,Preparation.loadedSlots(Map.of(),Map.of(box,1,RANARR,3,SNAP,4),Map.of(),Map.of(),id->id!=box));
        assertEquals(Set.of(box),GuideView.missingItems(Map.of(box,1),Map.of()));
        assertTrue(GuideView.missingItems(Map.of(open,1),Map.of(open,1)).isEmpty());
    }
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
        assertEquals(2,Preparation.loadedSlots(s.bag,Map.of(RANARR,7),s.equipped,s.seedBox,id->id==RANARR));
        PhysicalStock absent=new PhysicalStock(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(RANARR,7),id->"",id->id);
        assertFalse(absent.carried.containsKey(RANARR));
    }
    @Test public void closedBoxTransfersAreConfirmedByLooseDelta()
    {
        SeedBoxStock box=new SeedBoxStock();
        Map<Integer,Integer> bag=Map.of(ItemID.SEED_BOX,1,RANARR,10);
        box.begin(true,RANARR,bag,1);
        assertFalse(box.settle(bag,2,id->id==RANARR));
        assertTrue(box.settle(Map.of(ItemID.SEED_BOX,1,RANARR,3),3,id->id==RANARR));
        assertEquals(7,(int)box.carried(bag).get(RANARR));assertFalse(box.complete());
        assertFalse(box.settle(Map.of(),4,id->true));
        box.begin(false,RANARR,Map.of(RANARR,3),5);
        assertTrue(box.settle(Map.of(RANARR,6),6,id->id==RANARR));
        assertEquals(4,(int)box.carried(bag).get(RANARR));
    }
    @Test public void nativeSnapshotWinsAndCacheIsNotInventedStock()
    {
        SeedBoxStock box=new SeedBoxStock();
        box.restore(RANARR+":20");assertEquals(20,box.cachedTotal());
        assertTrue(box.carried(Map.of(ItemID.SEED_BOX,1)).isEmpty());
        ItemContainer nativeBox=mock(ItemContainer.class);
        when(nativeBox.getItems()).thenReturn(new Item[]{new Item(RANARR,7)});
        box.begin(true,RANARR,Map.of(RANARR,10),3);
        box.observe(nativeBox,4);
        assertFalse(box.settle(Map.of(RANARR,3),4,id->true));
        assertEquals(7,(int)box.carried(Map.of(ItemID.SEED_BOX,1)).get(RANARR));
        assertFalse(box.chat("You put 7 x Ranarr seed straight into your open seed box.",4,n->RANARR));
        box.forget();assertEquals(7,box.cachedTotal());assertFalse(box.known());
    }
    @Test public void confirmedPlantAndCheckReconcile()
    {
        SeedBoxStock box=new SeedBoxStock();
        box.chat("The seed box contains:",1,n->RANARR);
        box.chat("9 x Ranarr seed",1,n->RANARR);
        assertFalse(box.finishCheck(2));assertTrue(box.finishCheck(3));assertTrue(box.complete());assertFalse(box.orderKnown());
        Crop crop=CropCatalog.findByItemId(RANARR);
        assertFalse(box.planted(crop,1,0,box.revision()));
        assertTrue(box.planted(crop,0,0,box.revision()));
        assertEquals(8,(int)box.carried(Map.of(ItemID.SEED_BOX,1)).get(RANARR));
        assertTrue(box.chat("You put 2 x Ranarr seed straight into your open seed box.",3,n->RANARR));
        assertEquals(10,(int)box.carried(Map.of(ItemID.SEED_BOX,1)).get(RANARR));
        assertFalse(box.orderKnown());
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

    @Test public void incompleteNativeAndDelayedChatNeverInventOrEraseStock()
    {
        SeedBoxStock box=new SeedBoxStock();ItemContainer container=mock(ItemContainer.class);
        Map<Integer,Integer> bag=Map.of(ItemID.SEED_BOX,1);
        when(container.getItems()).thenReturn(new Item[]{new Item(RANARR,7)});
        assertTrue(box.observe(container,10,id->id==RANARR));
        when(container.getItems()).thenReturn((Item[])null);
        assertFalse(box.observe(container,11,id->true));
        assertEquals(7,(int)box.carried(bag).get(RANARR));
        when(container.getItems()).thenReturn(new Item[]{new Item(RANARR,9)});
        assertTrue(box.observe(container,12,id->id==RANARR));
        assertFalse(box.chat("You put 2 x Ranarr seed straight into your open seed box.",13,n->RANARR));
        assertEquals(9,(int)box.carried(bag).get(RANARR));
        when(container.getItems()).thenReturn(new Item[]{new Item(SNAP,2)});
        assertFalse(box.observe(container,14,id->id==RANARR));
        assertEquals(9,(int)box.carried(bag).get(RANARR));
    }
    @Test public void chatCheckWaitsForDelayedLinesAndMutationCancelsStaleCheck()
    {
        SeedBoxStock box=new SeedBoxStock();
        Map<Integer,Integer> bag=Map.of(ItemID.SEED_BOX,1);
        java.util.function.ToIntFunction<String> ids=n->n.startsWith("Ranarr")?RANARR:SNAP;
        box.chat("The seed box contains:",1,ids);box.chat("9 x Ranarr seed",1,ids);
        assertFalse(box.finishCheck(2));box.chat("3 x Snapdragon seed",2,ids);
        assertFalse(box.finishCheck(3));assertTrue(box.finishCheck(4));
        assertEquals(Map.of(RANARR,9,SNAP,3),box.carried(bag));
        box.chat("The seed box contains:",5,ids);box.chat("9 x Ranarr seed",5,ids);
        assertTrue(box.chat("You put 2 x Ranarr seed straight into your open seed box.",6,ids));
        assertFalse(box.finishCheck(8));assertEquals(Map.of(RANARR,11,SNAP,3),box.carried(bag));
    }
    @Test public void overlappingTransfersInvalidateOldCountsAndSameTickCannotSettle()
    {
        SeedBoxStock box=new SeedBoxStock();ItemContainer container=mock(ItemContainer.class);
        when(container.getItems()).thenReturn(new Item[]{new Item(RANARR,7)});
        box.observe(container);
        box.begin(true,RANARR,Map.of(RANARR,10),1);
        assertFalse(box.settle(Map.of(RANARR,8),1,id->true));
        box.begin(true,RANARR,Map.of(RANARR,8),1);
        assertFalse(box.complete());assertEquals(7,box.cachedTotal());
        assertTrue(box.settle(Map.of(RANARR,5),2,id->true));
        assertEquals(Map.of(RANARR,3),box.carried(Map.of(ItemID.SEED_BOX,1)));
    }

    @Test public void nativePickupCreditIsConsumedOnlyOnce()
    {
        SeedBoxStock box=new SeedBoxStock();ItemContainer c=mock(ItemContainer.class);
        when(c.getItems()).thenReturn(new Item[]{new Item(RANARR,5)});
        box.observe(c,1);
        when(c.getItems()).thenReturn(new Item[]{new Item(RANARR,6)});
        box.observe(c,2);
        String pickup="You put 1 x Ranarr seed straight into your open seed box.";
        assertFalse(box.chat(pickup,2,n->RANARR));
        assertTrue(box.chat(pickup,3,n->RANARR));
        assertEquals(7,(int)box.carried(Map.of(ItemID.SEED_BOX,1)).get(RANARR));
        box.orderUncertain();assertFalse(box.orderKnown());assertTrue(box.complete());
    }

    @Test public void firstNativeObservationCannotBeDoubleCountedByDelayedChat()
    {
        SeedBoxStock box=new SeedBoxStock();ItemContainer c=mock(ItemContainer.class);
        when(c.getItems()).thenReturn(new Item[]{new Item(RANARR,7)});
        assertTrue(box.observe(c,10,id->true));
        assertFalse(box.chat("You put 7 x Ranarr seed straight into your open seed box.",11,n->RANARR));
        assertEquals(7,(int)box.carried(Map.of(ItemID.SEED_BOX,1)).get(RANARR));
        assertFalse(box.complete());assertFalse(box.orderKnown());
        assertTrue(box.chat("You put 1 x Ranarr seed straight into your open seed box.",13,n->RANARR));
        assertEquals(8,(int)box.carried(Map.of(ItemID.SEED_BOX,1)).get(RANARR));
    }
    @Test public void multipleNativeIncrementsAndDelayedMessagesReconcileOnlyOnce()
    {
        SeedBoxStock box=new SeedBoxStock();ItemContainer c=mock(ItemContainer.class);
        when(c.getItems()).thenReturn(new Item[]{new Item(RANARR,5)});
        box.observe(c,1);
        when(c.getItems()).thenReturn(new Item[]{new Item(RANARR,6)});
        box.observe(c,2);
        when(c.getItems()).thenReturn(new Item[]{new Item(RANARR,6),new Item(SNAP,1)});
        box.observe(c,2);
        assertFalse(box.chat("You put 1 x Ranarr seed straight into your open seed box.",3,n->RANARR));
        assertFalse(box.chat("You put 1 x Snapdragon seed straight into your open seed box.",3,n->SNAP));
        assertEquals(Map.of(RANARR,6,SNAP,1),box.carried(Map.of(ItemID.SEED_BOX,1)));
        assertFalse(SeedBoxStock.conflictingLooseAction("walk here"));
        assertFalse(SeedBoxStock.conflictingLooseAction("examine"));
        assertFalse(SeedBoxStock.conflictingLooseAction("use"));
        for(String option:List.of("take","drop","withdraw all","deposit 1","destroy"))
            assertTrue(option,SeedBoxStock.conflictingLooseAction(option));
    }
}
