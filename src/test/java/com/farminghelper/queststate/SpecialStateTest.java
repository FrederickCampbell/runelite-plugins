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

    private CropState state(PatchImplementation type, int value)
    {
        return QuestFarmingStateReader.normalizedState(type, value, type.forVarbitValue(value));
    }
}
