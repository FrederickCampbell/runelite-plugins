package com.farminghelper;

import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.gameval.ItemID;

public class CompostChoiceTest
{
    @Test public void legacyBooleanMigratesWithoutLosingPreference()
    {
        assertEquals(CompostChoice.BOTTOMLESS, CompostChoice.load("", true));
        assertEquals(CompostChoice.NONE, CompostChoice.load("", false));
        assertEquals(CompostChoice.SUPER, CompostChoice.load("SUPER", true));
    }

    @Test public void selectedCompostIsBudgetedExactly()
    {
        java.util.Map<Integer,Integer> required=new java.util.HashMap<>();
        Supplies.addCompost(required,ItemID.BUCKET_SUPERCOMPOST,3);
        Supplies.addCompost(required,-1,2);
        assertEquals(java.util.Map.of(ItemID.BUCKET_SUPERCOMPOST,3),required);
        assertEquals(ItemID.BUCKET_ULTRACOMPOST, CompostChoice.BOTTOMLESS.resolve(false));
        assertEquals(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, CompostChoice.BOTTOMLESS.resolve(true));
    }
}
