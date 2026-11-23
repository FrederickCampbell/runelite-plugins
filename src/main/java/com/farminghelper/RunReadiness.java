package com.farminghelper;

import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;

/** Unknown state is never proof that a patch is growing. */
final class RunReadiness
{
    private RunReadiness() {}

    static boolean growing(Observation observation)
    {
        return observation != null && observation.known() && observation.state == CropState.GROWING;
    }

    static boolean levelEligible(PatchType type, int farmingLevel)
    {
        return CropCatalog.crops(type).isEmpty() || CropCatalog.recommend(type, farmingLevel) != null;
    }

    /** Current OSRS spirit tree planting limits; POH trees do not count. */
    static int spiritTreeCapacity(int farmingLevel)
    {
        if (farmingLevel >= 99) return Integer.MAX_VALUE;
        if (farmingLevel >= 96) return 4;
        if (farmingLevel >= 93) return 3;
        if (farmingLevel >= 88) return 2;
        return farmingLevel >= 83 ? 1 : 0;
    }

    static boolean waitingForGrowth(int growing, int eligible)
    {
        return growing > 0 && growing == eligible;
    }
}
