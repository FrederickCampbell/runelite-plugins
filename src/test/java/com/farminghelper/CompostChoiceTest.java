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

    @Test public void nativeTreeCompostTiersUseSeparatePhysicalPatchIds()
    {
        var varrock=QuestTargets.get(new FarmRunPatch("Varrock","",PatchType.TREE));
        var stronghold=QuestTargets.get(new FarmRunPatch("Gnome Stronghold","",PatchType.TREE));
        int v=net.runelite.api.gameval.VarbitID.FARMING_COMPOST_VARBIT_TREE_3_TRANSMIT;
        int g=net.runelite.api.gameval.VarbitID.FARMING_TREE_COMPOST_5_TRANSMIT;
        assertEquals(v,CompostChoice.liveVarbit(varrock.objectId));
        assertEquals(g,CompostChoice.liveVarbit(stronghold.objectId));
        var client=org.mockito.Mockito.mock(net.runelite.api.Client.class);
        org.mockito.Mockito.when(client.getVarbitValue(v)).thenReturn(3,0,3);
        org.mockito.Mockito.when(client.getVarbitValue(g)).thenReturn(2);
        assertEquals(3,CompostChoice.liveTier(client,v));
        assertEquals(0,CompostChoice.liveTier(client,v)); // newly cleared
        assertEquals(3,CompostChoice.liveTier(client,v)); // freshly treated
        assertEquals(2,CompostChoice.liveTier(client,g));
        assertEquals(-1,CompostChoice.liveVarbit(8210)); // transformed crop is not an identity
        assertEquals(-1,CompostChoice.liveTier(client,-1));
        assertEquals(3,CompostChoice.minimumTier(ItemID.BUCKET_ULTRACOMPOST));
        assertEquals(2,CompostChoice.minimumTier(ItemID.BUCKET_SUPERCOMPOST));
        assertEquals(1,CompostChoice.minimumTier(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED));
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
