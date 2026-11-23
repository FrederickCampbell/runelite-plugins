package com.farmingpatchadvisor;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.gameval.ItemID;

public class DirectorCompostChoiceTest
{
    @Test public void legacyBooleanMigratesWithoutLosingPreference()
    {
        assertEquals(DirectorCompostChoice.BOTTOMLESS, DirectorCompostChoice.load("", true));
        assertEquals(DirectorCompostChoice.NONE, DirectorCompostChoice.load("", false));
        assertEquals(DirectorCompostChoice.SUPER, DirectorCompostChoice.load("SUPER", true));
    }

    @Test public void selectedCompostIsBudgetedExactly()
    {
        FarmRunPatch patch = new FarmRunPatch("Catherby", "", PatchType.HERB);
        assertEquals(Integer.valueOf(1), DirectorSupplies.required(Arrays.asList(patch), ItemID.BUCKET_SUPERCOMPOST, false, false).get(ItemID.BUCKET_SUPERCOMPOST));
        assertFalse(DirectorSupplies.required(Arrays.asList(patch), -1, false, false).containsKey(ItemID.BUCKET_ULTRACOMPOST));
        assertEquals(ItemID.BUCKET_ULTRACOMPOST, DirectorCompostChoice.BOTTOMLESS.resolve(false));
        assertEquals(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, DirectorCompostChoice.BOTTOMLESS.resolve(true));
    }
}
