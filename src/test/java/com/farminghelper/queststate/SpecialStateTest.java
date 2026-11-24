package com.farminghelper.queststate;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpecialStateTest
{
    @Test public void knownSpecialHealthChecksAndExpiredAnimaAreActionable()
    {
        assertEquals(CropState.UNCHECKED, state(PatchImplementation.SPIRIT_TREE, 44));
        assertEquals(CropState.GROWING, state(PatchImplementation.SPIRIT_TREE, 20));
        assertEquals(CropState.UNCHECKED, state(PatchImplementation.REDWOOD, 37));
        assertEquals(CropState.UNCHECKED, state(PatchImplementation.CELASTRUS, 13));
        assertEquals(CropState.UNCHECKED, state(PatchImplementation.CRYSTAL_TREE, 14));
        assertEquals(CropState.DEAD, state(PatchImplementation.ANIMA, 16));
        assertEquals(CropState.DEAD, state(PatchImplementation.ANIMA, 25));
        assertEquals(CropState.DEAD, state(PatchImplementation.ANIMA, 34));
        assertEquals(CropState.EMPTY, state(PatchImplementation.ANIMA, 0));
    }

    @Test public void matureCoralIsHarvestable()
    {
        assertEquals(CropState.HARVESTABLE,state(PatchImplementation.CORAL,8));
        assertEquals(CropState.HARVESTABLE,state(PatchImplementation.CORAL,19));
        assertEquals(CropState.HARVESTABLE,state(PatchImplementation.CORAL,30));
    }

    @Test public void kastoriFlowerIsNotATreeAndWeedsAreEmpty()
    {
        FarmingWorld world=new FarmingWorld();
        FarmingPatch flower=QuestFarmingStateReader.find(world,net.runelite.client.plugins.timetracking.Tab.FLOWER,"Kastori","",PatchImplementation.FLOWER);
        assertNotNull(flower);
        assertEquals(net.runelite.api.gameval.VarbitID.FARMING_TRANSMIT_C,flower.getVarbit());
        assertTrue(QuestFarmingStateReader.matchesLiveName("Flower Patch",true));
        assertFalse(QuestFarmingStateReader.matchesLiveName("Calquat tree",true));
        assertFalse(QuestFarmingStateReader.matchesLiveName("Fruit tree",true));
        assertEquals(CropState.EMPTY,state(PatchImplementation.FLOWER,0));
        assertEquals(CropState.HARVESTABLE,state(PatchImplementation.COMPOST,94));
        assertEquals(CropState.HARVESTABLE,state(PatchImplementation.BIG_COMPOST,99));
    }

    private CropState state(PatchImplementation type, int value)
    {
        return QuestFarmingStateReader.normalizedState(type, value, type.forVarbitValue(value));
    }
}
