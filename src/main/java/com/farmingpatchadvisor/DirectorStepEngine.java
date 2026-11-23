package com.farmingpatchadvisor;

import javax.annotation.Nullable;
import com.farmingpatchadvisor.queststate.CropState;

/**
 * The conditional-step semantics adapted from Quest Helper HerbRun/TreeRun:
 * evaluate the observed patch state before choosing an action. This model
 * deliberately does not issue game clicks. Unknown state is NOT empty.
 */
final class DirectorStepEngine
{
    enum Action
    {
        INSPECT,
        HARVEST,
        CHECK_HEALTH,
        REMOVE_TREE,
        DIG_STUMP,
        CLEAR_DEAD,
        CURE,
        PLANT,
        PROTECT_TREE,
        COMPLETE
    }

    private DirectorStepEngine() {}

    static Action action(PatchType type, @Nullable CropState state,
                         boolean requireProtection, @Nullable Boolean protectedByFarmer)
    {
        if (state == null)
        {
            return Action.INSPECT;
        }
        switch (state)
        {
            case EMPTY:
                return Action.PLANT;
            case DEAD:
                return Action.CLEAR_DEAD;
            case DISEASED:
                return Action.CURE;
            case STUMP:
                return Action.DIG_STUMP;
            case UNCHECKED:
                return type == PatchType.TREE ? Action.CHECK_HEALTH : Action.HARVEST;
            case HARVESTABLE:
                return type == PatchType.TREE ? Action.REMOVE_TREE : Action.HARVEST;
            case GROWING:
                if (type == PatchType.TREE && requireProtection
                    && !Boolean.TRUE.equals(protectedByFarmer))
                {
                    return Action.PROTECT_TREE;
                }
                return Action.COMPLETE;
            default:
                return Action.INSPECT;
        }
    }

    static String instruction(Action action, Crop crop, PatchType type)
    {
        switch (action)
        {
            case HARVEST:
                return "Harvest the herbs, then compost and plant "
                    + crop.getName() + ".";
            case CHECK_HEALTH:
                return "Check the tree's health, then remove the tree.";
            case REMOVE_TREE:
                return "Chop the tree or pay the gardener to remove it.";
            case DIG_STUMP:
                return "Dig out the tree stump with a spade.";
            case CLEAR_DEAD:
                return "Clear the dead crop, then plant " + crop.getName() + ".";
            case CURE:
                return "Cure the diseased crop before harvesting.";
            case PLANT:
                return type == PatchType.TREE
                    ? "Rake if necessary, compost and plant " + crop.getName() + "."
                    : "Rake if necessary, harvest if needed, compost and plant "
                        + crop.getName() + ".";
            case PROTECT_TREE:
                return "Ask the gardener to protect the tree. Continue automatically "
                    + "when protection is detected, or press Next to confirm.";
            case COMPLETE:
                return "Already growing. This patch can be skipped.";
            default:
                return "Patch state unknown. Inspect the patch or visit it to load "
                    + "live Farming data; use Next as a manual fallback.";
        }
    }

    static boolean canAutoAdvance(Action action, boolean known, boolean sourceFresh,
                                  boolean alreadyGrowingAtActivation)
    {
        // An observed growing state when a step was selected may be skipped
        // exactly as Quest Helper skips growing patches. Newly changed states
        // require live varbits or a new Time Tracking observation.
        return action == Action.COMPLETE && known &&
            (sourceFresh || alreadyGrowingAtActivation);
    }
}
