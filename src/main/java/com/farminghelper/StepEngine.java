package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
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
        PAY_REMOVE,
        CHOP_TREE,
        RAKE,
        DIG_STUMP,
        CLEAR_DEAD,
        CLEAR_LIVING,
        NEED_REPLACEMENT,
        CURE,
        PLANT,
        COMPOST,
        PROTECT_TREE,
        WATER,
        COMPLETE
    }

    private StepEngine() {}

    static String label(Action action,PatchType type)
    {
        switch(action)
        {
            case INSPECT:return "Inspect";
            case HARVEST:return "Harvest";
            case CHECK_HEALTH:return "Check health";
            case PAY_REMOVE:return "Pay to remove";
            case CHOP_TREE:return "Chop";
            case RAKE:return "Rake";
            case DIG_STUMP:return "Dig stump";
            case CLEAR_DEAD:case CLEAR_LIVING:return "Clear";
            case NEED_REPLACEMENT:return "Bring seed / sapling";
            case CURE:return CropCare.prunes(type)?"Prune":"Cure";
            case PLANT:return "Plant";
            case COMPOST:return "Compost";
            case PROTECT_TREE:return "Protect";
            case WATER:return "Water";
            default:return "";
        }
    }

    static Action action(PatchType type,Observation observation,boolean preserve,boolean replacementCarried,
        boolean clearOnly,boolean payGardener)
    {
        CropState state=observation.state;
        if(state==null)return Action.INSPECT;
        if(CropReplacement.configurable(type)&&CropReplacement.mature(type,observation))
        {
            if(state==CropState.UNCHECKED)return Action.CHECK_HEALTH;
            if(CropReplacement.producer(type)&&!CropReplacement.depleted(type,observation))return Action.HARVEST;
            if(preserve)return Action.COMPLETE;
            if(!replacementCarried&&!clearOnly)return Action.NEED_REPLACEMENT;
            if(type==PatchType.REDWOOD)return Action.PAY_REMOVE;
            if(type==PatchType.BUSH||type==PatchType.CACTUS||type==PatchType.CALQUAT)return Action.CLEAR_LIVING;
            return payGardener?Action.PAY_REMOVE:Action.CHOP_TREE;
        }
        switch(state)
        {
            case EMPTY:return Action.PLANT;
            case DEAD:return Action.CLEAR_DEAD;
            case DISEASED:return Action.CURE;
            case STUMP:return Action.DIG_STUMP;
            case UNCHECKED:return type.isTree()?Action.CHECK_HEALTH:Action.HARVEST;
            case HARVESTABLE:return type==PatchType.SPIRIT_TREE?Action.COMPLETE:Action.HARVEST;
            case GROWING:return Action.COMPLETE;
            default:return Action.INSPECT;
        }
    }

    static boolean keepExisting(PatchType type, CropState state)
    {
        if (state != CropState.UNCHECKED && state != CropState.HARVESTABLE && state != CropState.DISEASED)
            return false;
        return type == PatchType.SPIRIT_TREE || type == PatchType.REDWOOD ||
            type == PatchType.BUSH || type == PatchType.CACTUS;
    }

    static String instruction(Action action,Crop crop,PatchType type,boolean keep,boolean clearOnly,boolean empty)
    {
        String name=crop==null?"a suitable replacement crop":crop.getName();
        if(action==Action.PAY_REMOVE&&type==PatchType.REDWOOD)return "Pay Alexandra 2,000 coins to remove the mature redwood.";
        if(action==Action.CHECK_HEALTH&&CropReplacement.configurable(type))
            return "Check health"+(CropReplacement.producer(type)?", then harvest produce.":".")+(keep?" Preserve the crop.":" Replant when replacement supplies are carried.");
        if(action==Action.HARVEST&&keep&&CropReplacement.producer(type))return "Harvest the produce, then leave this plant standing.";
        if(clearOnly)return ClearOnly.instruction(action,type);
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
                return "Check the tree’s health, then remove the tree if replanting.";
            case PAY_REMOVE: return "Pay the gardener 200 coins to remove the tree.";
            case CHOP_TREE: return "Chop the tree.";
            case RAKE: return "Rake the weeds.";
            case DIG_STUMP:
                return "Dig out the tree stump with a spade.";
            case CLEAR_LIVING: return "Clear the mature crop using your spade.";
            case NEED_REPLACEMENT: return empty?"Bring a plantable "+name+". Seeds and seedlings must be prepared first.":"Bring "+name+" before clearing the living crop, or Skip to leave it planted." ;
            case CLEAR_DEAD:
                return "Clear the dead crop, then plant " + name + ".";
            case CURE:
                return type == PatchType.CORAL ? "Prune the diseased coral with secateurs." : CropCare.prunes(type) ? "Prune the diseased crop with secateurs."
                    : "Cure the diseased crop before harvesting.";
            case WATER:
                return "Water this crop with the watering can you are carrying.";
            case PLANT:
                if (type == PatchType.GRAPEVINE && crop != null) return "Prepare the vine patch with saltpetre and a gardening trowel, then plant " + name + ".";
                return crop == null ? "Choose an eligible crop manually, or skip this patch."
                    : "Rake if necessary, plant " + name + ".";
            case COMPOST:
                return "Apply compost.";
            case PROTECT_TREE:
                return "Ask the gardener to protect the crop. Continue automatically "
                    + "when protection is detected, or use Skip if protection was applied but not detected.";
            case COMPLETE:
                return crop == null && type.isTree() ? "Existing tree checked; leave it planted."
                    : "Patch complete. Continue to the next stop.";
            default:
                return "Patch state unknown. Inspect the patch or visit it to load "
                    + "live Farming data; use Skip to omit this patch.";
        }
    }

}
