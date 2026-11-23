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

    static boolean waitingForGrowth(int growing, int eligible)
    {
        return growing > 0 && growing == eligible;
    }
}
