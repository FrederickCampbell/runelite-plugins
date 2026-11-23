package com.farminghelper;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ItemID;
import java.util.Arrays;
public class QuestTargetsTest
{
    @Test public void catalogContainsOriginalTreeAndHerbTargets()
    {
        assertEquals(32, QuestTargets.count());
        FarmRunPatch lumbridge = new FarmRunPatch("Lumbridge", "", PatchType.TREE);
        QuestTargets.Definition d = QuestTargets.get(lumbridge);
        assertEquals(ObjectID.FARMING_TREE_PATCH_4, d.objectId);
        assertEquals(NpcID.FARMING_GARDENER_TREE_4, d.gardenerId);
        assertNull(QuestTargets.get(new FarmRunPatch("Falador", "North West", PatchType.ALLOTMENT)));
        assertEquals(Integer.valueOf(200), Supplies.required(Arrays.asList(lumbridge), false,false,false,true).get(ItemID.COINS));
    }
    @Test public void removalAndRakingAreSeparateInteractions()
    {
        assertEquals(StepEngine.Action.PAY_REMOVE, StepEngine.refine(StepEngine.Action.REMOVE_TREE,true,true,false));
        assertEquals(StepEngine.Action.CHOP_TREE, StepEngine.refine(StepEngine.Action.REMOVE_TREE,true,false,false));
        assertEquals(StepEngine.Action.RAKE, StepEngine.refine(StepEngine.Action.PLANT,false,false,true));
    }
}
