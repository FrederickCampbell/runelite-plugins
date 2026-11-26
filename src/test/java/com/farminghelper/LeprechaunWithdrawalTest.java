package com.farminghelper;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class LeprechaunWithdrawalTest
{
    @Test public void actualCanVariantAlwaysUsesSingleWateringSlot()
    {
        for(int id:new int[]{5333,5340,13353})
        {
            LeprechaunWithdrawal p=LeprechaunWithdrawal.plan(id,true,20,1,10,"Watering can");
            assertEquals(12,p.child);assertEquals(1,p.quantity);assertEquals("Remove 1",p.label());
        }
    }
    @Test public void looseCompostIsBoundedByNeedStockAndSpace()
    {
        LeprechaunWithdrawal p=LeprechaunWithdrawal.plan(ItemID.BUCKET_ULTRACOMPOST,false,8,6,3,"Ultracompost");
        assertEquals(19,p.child);assertEquals(3,p.quantity);assertEquals("Remove-X: 3",p.label());
        assertEquals(17,LeprechaunWithdrawal.plan(ItemID.BUCKET_COMPOST,false,1,8,8,"Compost").child);
        assertEquals(18,LeprechaunWithdrawal.plan(ItemID.BUCKET_SUPERCOMPOST,false,5,8,8,"Supercompost").child);
        assertEquals("Remove-5",LeprechaunWithdrawal.plan(ItemID.BUCKET_SUPERCOMPOST,false,5,8,8,"Supercompost").label());
    }
    @Test public void fullInventoryAndBottomlessBucket()
    {
        LeprechaunWithdrawal p=LeprechaunWithdrawal.plan(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED,false,30,1,2,"Bottomless bucket");
        assertEquals(15,p.child);assertEquals(1,p.quantity);
        p=LeprechaunWithdrawal.plan(ItemID.BUCKET_ULTRACOMPOST,false,9,9,0,"Ultracompost");
        assertEquals(0,p.quantity);assertEquals("Free a slot",p.label());
    }
}
