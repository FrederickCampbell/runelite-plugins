package com.farminghelper;

import java.util.Arrays;
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
        FarmRunPatch patch = new FarmRunPatch("Catherby", "", PatchType.HERB);
        assertEquals(Integer.valueOf(1), Supplies.required(Arrays.asList(patch), ItemID.BUCKET_SUPERCOMPOST, false, false).get(ItemID.BUCKET_SUPERCOMPOST));
        assertFalse(Supplies.required(Arrays.asList(patch), -1, false, false).containsKey(ItemID.BUCKET_ULTRACOMPOST));
        assertEquals(ItemID.BUCKET_ULTRACOMPOST, CompostChoice.BOTTOMLESS.resolve(false));
        assertEquals(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, CompostChoice.BOTTOMLESS.resolve(true));
    }
}
