package com.farminghelper;

import javax.annotation.Nullable;
import com.farminghelper.queststate.CropState;

/**
 * The conditional-step semantics adapted from Quest Helper HerbRun/TreeRun:
 * evaluate the observed patch state before choosing an action. This model
 * deliberately does not issue game clicks. Unknown state is NOT empty.
 */
final class StepEngine
{
    enum Action
    {
        INSPECT,
        HARVEST,
        CHECK_HEALTH,
        REMOVE_TREE,
        PAY_REMOVE,
        CHOP_TREE,
        RAKE,
        DIG_STUMP,
        CLEAR_DEAD,
        CLEAR_LIVING,
        CURE,
        PLANT,
        COMPOST,
        PROTECT_TREE,
        WATER,
        COMPLETE
    }

    private StepEngine() {}

    static boolean supportsProtection(PatchType type)
    {
        return type == PatchType.TREE || type == PatchType.FRUIT_TREE ||
            type == PatchType.CALQUAT || type == PatchType.HARDWOOD_TREE ||
            type == PatchType.CELASTRUS || type == PatchType.REDWOOD || type == PatchType.CORAL || type == PatchType.SEAWEED ||
            type == PatchType.ALLOTMENT || type == PatchType.HOPS || type == PatchType.BUSH || type == PatchType.CACTUS || type == PatchType.SPIRIT_TREE;
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
                return isTreeType(type) || type == PatchType.CACTUS || type == PatchType.BUSH ? Action.CHECK_HEALTH : Action.HARVEST;
            case HARVESTABLE:
                if (type == PatchType.SPIRIT_TREE || type == PatchType.REDWOOD) return Action.COMPLETE;
                if (type == PatchType.CELASTRUS || type == PatchType.CRYSTAL_TREE) return Action.HARVEST;
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

    static boolean keepExisting(PatchType type, CropState state)
    {
        if (state != CropState.UNCHECKED && state != CropState.HARVESTABLE && state != CropState.DISEASED)
            return false;
        return type == PatchType.SPIRIT_TREE || type == PatchType.REDWOOD ||
            type == PatchType.BUSH || type == PatchType.CACTUS;
    }

    static Action refine(Action action, boolean payRemoval, boolean knownGardener, boolean weeds)
    {
        if (action == Action.REMOVE_TREE) return payRemoval && knownGardener ? Action.PAY_REMOVE : Action.CHOP_TREE;
        if (action == Action.PLANT && weeds) return Action.RAKE;
        return action;
    }

    static String instruction(Action action, Crop crop, PatchType type)
    {
        return instruction(action, crop, type, true);
    }

    static String instruction(Action action, Crop crop, PatchType type, boolean compost)
    {
        String name = crop == null ? "a suitable replacement crop" : crop.getName();
        switch (action)
        {
            case HARVEST:
                if (type == PatchType.HESPORI)
                    return "Prepare for Hespori combat if alive; harvest only after defeating it. Replant manually when appropriate.";
                if (type == PatchType.CORAL) return "Collect the mature coral, then replant if the nursery is empty.";
                if (type == PatchType.CRYSTAL_TREE) return "Harvest the crystal shards, then plant a crystal sapling if the patch is empty.";
                if (type == PatchType.CELASTRUS)
                    return crop == null ? "Harvest the bark and leave the tree planted; no replacement allocated."
                        : "Harvest the bark if available, then remove the harvested tree and replant if desired.";
                if (crop == null && (type == PatchType.FRUIT_TREE || type == PatchType.CALQUAT))
                    return "Pick the fruit and leave the tree planted. Use Skip if finished and the patch has not advanced.";
                return crop == null
                    ? "Harvest this patch and leave the existing crop planted if it regrows."
                    : "Harvest this " + type.getDisplayName().toLowerCase() +
                        " patch, then replant " + name + " if appropriate.";
            case CHECK_HEALTH:
                if (type == PatchType.SPIRIT_TREE) return "Check health and leave the spirit tree planted for travel.";
                if (type == PatchType.REDWOOD) return "Check health and leave the redwood planted.";
                if (type == PatchType.CACTUS) return "Check health and pick the cactus produce.";
                if (type == PatchType.BUSH) return "Check health, then harvest the berries.";
                if (type == PatchType.CELASTRUS) return crop == null
                    ? "Check health, then harvest the bark and leave the tree planted."
                    : "Check health, then harvest the bark before removing the tree.";
                return "Check the tree’s health, then remove the tree if replanting.";
            case REMOVE_TREE:
                return "Chop the tree or pay the gardener to remove it.";
            case PAY_REMOVE: return "Pay the gardener 200 coins to remove the tree.";
            case CHOP_TREE: return "Chop the tree.";
            case RAKE: return "Rake the weeds.";
            case DIG_STUMP:
                return "Dig out the tree stump with a spade.";
            case CLEAR_LIVING: return "Clear the mature crop using your spade.";
            case CLEAR_DEAD:
                return "Clear the dead crop, then plant " + name + ".";
            case CURE:
                return type == PatchType.CORAL ? "Prune the diseased coral with secateurs." : CropCare.prunes(type) ? "Prune the diseased crop with secateurs."
                    : "Cure the diseased crop before harvesting.";
            case WATER:
                return "Water this growing crop with the watering can, then continue.";
            case PLANT:
                if (type == PatchType.GRAPEVINE && crop != null) return "Prepare the vine patch with saltpetre and a gardening trowel, then plant " + name + ".";
                return crop == null ? "Choose an eligible crop manually, or skip this patch."
                    : "Rake if necessary, " +
                        (compost && type.usesCompost() ? "compost, " : "") + "plant " + name + ".";
            case COMPOST:
                return "Apply compost to this patch. Continue when confirmed, or use Skip if already treated and the patch has not advanced.";
            case PROTECT_TREE:
                return "Ask the gardener to protect the crop. Continue automatically "
                    + "when protection is detected, or use Skip if protection was applied but not detected.";
            case COMPLETE:
                return crop == null && isTreeType(type) ? "Existing tree checked; leave it planted."
                    : "Patch complete. Continue to the next stop.";
            default:
                return "Patch state unknown. Inspect the patch or visit it to load "
                    + "live Farming data; use Skip to omit this patch.";
        }
    }

}
