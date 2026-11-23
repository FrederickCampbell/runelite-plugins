package com.farmingpatchadvisor;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ItemID;
import java.util.Arrays;
public class DirectorQuestTargetsTest
{
    @Test public void catalogContainsOriginalTreeAndHerbTargets()
    {
        assertEquals(32, DirectorQuestTargets.count());
        FarmRunPatch lumbridge = new FarmRunPatch("Lumbridge", "", PatchType.TREE);
        DirectorQuestTargets.Definition d = DirectorQuestTargets.get(lumbridge);
        assertEquals(ObjectID.FARMING_TREE_PATCH_4, d.objectId);
        assertEquals(NpcID.FARMING_GARDENER_TREE_4, d.gardenerId);
        assertNull(DirectorQuestTargets.get(new FarmRunPatch("Falador", "North West", PatchType.ALLOTMENT)));
        assertEquals(Integer.valueOf(200), DirectorSupplies.required(Arrays.asList(lumbridge), false,false,false,true).get(ItemID.COINS));
    }
    @Test public void removalAndRakingAreSeparateInteractions()
    {
        assertEquals(DirectorStepEngine.Action.PAY_REMOVE, DirectorStepEngine.refine(DirectorStepEngine.Action.REMOVE_TREE,true,true,false));
        assertEquals(DirectorStepEngine.Action.CHOP_TREE, DirectorStepEngine.refine(DirectorStepEngine.Action.REMOVE_TREE,true,false,false));
        assertEquals(DirectorStepEngine.Action.RAKE, DirectorStepEngine.refine(DirectorStepEngine.Action.PLANT,false,false,true));
    }
}
