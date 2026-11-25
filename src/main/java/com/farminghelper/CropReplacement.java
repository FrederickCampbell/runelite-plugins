package com.farminghelper;

import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;

/** Per-type defaults and optional physical-patch overrides. Guidance never clears a live
 * crop without carried replacement supplies, unless the player explicitly chose clear-only. */
final class CropReplacement
{
    static boolean configurable(PatchType t)
    {
        switch(t)
        {
            case BUSH: case CACTUS: case TREE: case FRUIT_TREE: case HARDWOOD_TREE:
            case CALQUAT: case CELASTRUS: case REDWOOD:return true;
            default:return false;
        }
    }
    static boolean preferenceType(PatchType t){return (configurable(t)&&t!=PatchType.CELASTRUS)||t==PatchType.FLOWER;}
    static boolean defaultPreserve(PatchType t){return t==PatchType.CACTUS;}
    static boolean preserve(PatchType t,String typeValue,String patchValue)
    {
        if(t==PatchType.SPIRIT_TREE)return true;
        if(!preferenceType(t))return false;
        String mode=patchValue.isEmpty()?typeValue:patchValue;
        return "PRESERVE".equals(mode)||(!"REPLANT".equals(mode)&&defaultPreserve(t));
    }
    static boolean producer(PatchType t)
    {return t==PatchType.BUSH||t==PatchType.CACTUS||t==PatchType.FRUIT_TREE||t==PatchType.CALQUAT||t==PatchType.CELASTRUS;}
    static boolean mature(PatchType t,Observation o)
    {return o!=null&&(o.state==CropState.UNCHECKED||o.state==CropState.HARVESTABLE||o.state==CropState.GROWING&&QuestFarmingStateReader.matureDepleted(t.name(),o.rawValue));}
    static boolean depleted(PatchType t,Observation o)
    {return o!=null&&QuestFarmingStateReader.matureDepleted(t.name(),o.rawValue);}
    static boolean actionable(PatchType t,Observation o,boolean preserve)
    {
        if(o==null)return false;
        if(configurable(t)&&mature(t,o))
            return !preserve||o.state==CropState.UNCHECKED||producer(t)&&!depleted(t,o);
        return FarmingHelperPlugin.actionable(t,o.state);
    }
    static StepEngine.Action action(PatchType t,Observation o,boolean preserve,boolean replacementCarried,boolean clearOnly)
    {
        StepEngine.Action base=StepEngine.action(t,o.state,false,o.protectedByFarmer);
        if(!configurable(t)||!mature(t,o))return base;
        if(o.state==CropState.UNCHECKED)return StepEngine.Action.CHECK_HEALTH;
        if(producer(t)&&!depleted(t,o))return StepEngine.Action.HARVEST;
        if(preserve||!replacementCarried&&!clearOnly)return StepEngine.Action.COMPLETE;
        if(t==PatchType.REDWOOD)return StepEngine.Action.PAY_REMOVE;
        if(t==PatchType.BUSH||t==PatchType.CACTUS||t==PatchType.CALQUAT)return StepEngine.Action.CLEAR_LIVING;
        return StepEngine.Action.REMOVE_TREE;
    }
    private CropReplacement(){}
}
