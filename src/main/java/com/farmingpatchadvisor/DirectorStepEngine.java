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

    static boolean supportsProtection(PatchType type)
    {
        return type == PatchType.TREE || type == PatchType.FRUIT_TREE ||
            type == PatchType.CALQUAT || type == PatchType.HARDWOOD_TREE;
    }

    static boolean isTreeType(PatchType type)
    {
        switch (type)
        {
            case TREE:
            case FRUIT_TREE:
            case CALQUAT:
            case HARDWOOD_TREE:
            case SPIRIT_TREE:
            case CELASTRUS:
            case REDWOOD:
            case CRYSTAL_TREE:
                return true;
            default:
                return false;
        }
    }

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
                return isTreeType(type) ? Action.CHECK_HEALTH : Action.HARVEST;
            case HARVESTABLE:
                if (type == PatchType.SPIRIT_TREE) return Action.INSPECT;
                if (type == PatchType.CELASTRUS) return Action.HARVEST;
                return isTreeType(type) ? Action.REMOVE_TREE : Action.HARVEST;
            case GROWING:
                if (supportsProtection(type) && requireProtection
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
        String name = crop == null ? "a suitable replacement crop" : crop.getName();
        switch (action)
        {
            case HARVEST:
                if (type == PatchType.HESPORI)
                    return "Prepare for Hespori combat if alive; harvest only after defeating it. Replant manually when appropriate.";
                if (type == PatchType.CELASTRUS)
                    return "Harvest the bark if available, then remove the harvested tree and replant if desired.";
                return crop == null
                    ? "Harvest this patch; no replacement stock allocated. Refill and replan."
                    : "Harvest this " + type.getDisplayName().toLowerCase() +
                        " patch, then replant " + name + " if appropriate.";
            case CHECK_HEALTH:
                if (type == PatchType.SPIRIT_TREE) return "Check health and leave the spirit tree planted for travel.";
                if (type == PatchType.CELASTRUS) return "Check health, then harvest the bark before removing the tree.";
                return "Check the tree's health, then remove the tree if replanting.";
            case REMOVE_TREE:
                return "Chop the tree or pay the gardener to remove it.";
            case DIG_STUMP:
                return "Dig out the tree stump with a spade.";
            case CLEAR_DEAD:
                return "Clear the dead crop, then plant " + name + ".";
            case CURE:
                return isTreeType(type) ? "Prune the diseased tree with secateurs."
                    : "Cure the diseased crop before harvesting.";
            case PLANT:
                return crop == null ? "No suitable planting stock allocated. Bank or replan."
                    : isTreeType(type)
                    ? "Rake if necessary, compost and plant " + name + "."
                    : "Rake if necessary, harvest if needed, compost and plant "
                        + name + ".";
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

    static boolean canFinishHarvestOnly(boolean harvestOnly, boolean workWasObserved,
                                        CropState latestState, boolean live)
    {
        // Initial EMPTY must never imply that a ready crop was harvested.
        // Only a live transition from an observed actionable state is sufficient.
        return harvestOnly && workWasObserved && live && latestState == CropState.EMPTY;
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
