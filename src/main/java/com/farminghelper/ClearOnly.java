package com.farminghelper;

import com.farminghelper.queststate.CropState;

/** Clear-only is an explicit opt-in for seedless occupied actionable patches.
 * Never send players to empty/unknown/growing patches just to clear them. */
final class ClearOnly
{
    static boolean eligible(CropState state)
    {
        return state==CropState.UNCHECKED||state==CropState.HARVESTABLE||
            state==CropState.DEAD||state==CropState.STUMP;
    }
    static String instruction(StepEngine.Action action,PatchType type)
    {
        switch(action)
        {
            case CHECK_HEALTH:return "Check health for Farming XP, then clear the existing crop. No replacement allocated.";
            case PAY_REMOVE:return "Pay the gardener 200 coins to remove the tree. Leave the patch empty.";
            case CHOP_TREE:return "Chop the tree, then dig out the stump. Leave the patch empty.";
            case DIG_STUMP:return "Dig out the stump. Leave the patch empty.";
            case CLEAR_LIVING:return "Clear the mature crop. Leave the patch empty.";
            case CLEAR_DEAD:return "Clear the dead crop. Leave the patch empty.";
            case HARVEST:return type==PatchType.CELASTRUS?"Harvest the bark, then clear the tree. No replacement allocated.":"Harvest the crop. Clear the patch if needed; do not replant.";
            default:return "Clear the existing crop without replanting.";
        }
    }
    private ClearOnly(){}
}
