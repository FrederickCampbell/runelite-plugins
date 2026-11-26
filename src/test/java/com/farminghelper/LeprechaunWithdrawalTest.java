package com.farminghelper;

import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LeprechaunWithdrawalTest
{
    @Test public void allNativeSlotsAndImmediateQuantity()
    {
        int[] ids={ItemID.RAKE,ItemID.DIBBER,ItemID.SPADE,ItemID.SECATEURS,ItemID.FAIRY_ENCHANTED_SECATEURS,
            Supplies.GARDENING_TROWEL,ItemID.PLANT_CURE,ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED,ItemID.BUCKET_COMPOST,
            ItemID.BUCKET_SUPERCOMPOST,ItemID.BUCKET_ULTRACOMPOST};
        int[] children={8,9,10,11,11,13,14,15,17,18,19};
        for(int i=0;i<ids.length;i++)
        {
            LeprechaunWithdrawal p=LeprechaunWithdrawal.plan(ids[i],false,99,28,"Tool");
            assertEquals(children[i],p.child);assertEquals(1,p.quantity);
        }
        for(int id:new int[]{5333,5340,13353})
        {LeprechaunWithdrawal p=LeprechaunWithdrawal.plan(id,true,1,5,"Can");assertEquals(12,p.child);assertEquals(1,p.quantity);}
        assertNull(LeprechaunWithdrawal.plan(ItemID.RAKE,false,0,28,"Rake"));
        assertEquals("Free a slot",LeprechaunWithdrawal.plan(ItemID.BUCKET_ULTRACOMPOST,false,5,0,"Compost").label());
    }
    @Test public void currentActionUsesCarriedToolsAndBarbarianUnlock()
    {
        Map<Integer,Integer> empty=Map.of();
        assertEquals(ItemID.RAKE,LeprechaunWithdrawal.needed(StepEngine.Action.RAKE,PatchType.HERB,-1,false,empty,empty,-1));
        assertEquals(ItemID.SPADE,LeprechaunWithdrawal.needed(StepEngine.Action.HARVEST,PatchType.HERB,-1,false,empty,empty,-1));
        assertEquals(-1,LeprechaunWithdrawal.needed(StepEngine.Action.HARVEST,PatchType.FRUIT_TREE,-1,false,empty,empty,-1));
        assertEquals(ItemID.DIBBER,LeprechaunWithdrawal.needed(StepEngine.Action.PLANT,PatchType.HERB,-1,false,empty,empty,-1));
        assertEquals(-1,LeprechaunWithdrawal.needed(StepEngine.Action.PLANT,PatchType.HERB,-1,true,empty,empty,-1));
        assertEquals(-1,LeprechaunWithdrawal.needed(StepEngine.Action.CURE,PatchType.TREE,-1,false,empty,Map.of(ItemID.FAIRY_ENCHANTED_SECATEURS,1),-1));
        assertEquals(-1,LeprechaunWithdrawal.needed(StepEngine.Action.COMPOST,PatchType.HERB,-1,false,empty,empty,-1));
        assertEquals(-1,LeprechaunWithdrawal.needed(StepEngine.Action.WATER,PatchType.FLOWER,-1,false,empty,empty,5333));
    }
    @Test public void nativeStorageCountsAndActualSecateurs()
    {
        Client c=mock(Client.class);
        when(c.getVarbitValue(8359)).thenReturn(1);
        when(c.getVarbitValue(1438)).thenReturn(1);
        when(c.getVarbitValue(1848)).thenReturn(1);
        assertEquals(3,LeprechaunWithdrawal.stored(c,ItemID.SECATEURS));
        LeprechaunWithdrawal p=LeprechaunWithdrawal.resolve(c,StepEngine.Action.CURE,PatchType.TREE,-1,false,Map.of(),Map.of(),-1,10,
            id->id==ItemID.FAIRY_ENCHANTED_SECATEURS?"Magic secateurs":"Ordinary");
        assertEquals("Magic secateurs",p.name);assertEquals(11,p.child);assertEquals(1,p.quantity);
        assertNull(LeprechaunWithdrawal.resolve(c,StepEngine.Action.RAKE,PatchType.HERB,-1,false,Map.of(),Map.of(),-1,10,id->"Rake"));
    }
    @Test public void optionalWaterOfferEndsWithoutClick()
    {
        assertFalse(CropCare.offerExpired(2,1));
        assertTrue(CropCare.offerExpired(15,0));
        assertTrue(CropCare.offerExpired(1,6));
        assertEquals("Prune",StepEngine.label(StepEngine.Action.CURE,PatchType.TREE));
        assertEquals("Cure",StepEngine.label(StepEngine.Action.CURE,PatchType.HERB));
    }
}
