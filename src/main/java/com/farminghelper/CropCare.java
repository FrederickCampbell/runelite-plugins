package com.farminghelper;

import java.util.Map;
import java.util.regex.Pattern;
import net.runelite.api.gameval.VarbitID;
import java.util.function.IntFunction;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;

final class CropCare
{
    private static final Pattern CAN = Pattern.compile("watering can\\s*\\([1-8]\\)", Pattern.CASE_INSENSITIVE);

    // Native farming_tools_getstored uses varbit 1439 and enum 136.
    static int storedCan(net.runelite.api.Client client,IntFunction<String> name)
    {
        int key=client.getVarbitValue(VarbitID.FARMING_TOOLS_WATERINGCAN);
        if(key<=0)return -1;
        var variants=client.getEnum(136);
        if(variants==null)return -1;
        int id=variants.getIntValue(key);
        return id > 0 && charges(name.apply(id)) > 0 ? id : -1;
    }
    static int preferredCan(int carried,int stored,int bank)
    {return carried>0?carried:stored>0?stored:bank>0?bank:5333;}
    static int wateringCan(Map<Integer,Integer> bag,IntFunction<String> name)
    {
        return PhysicalStock.best(bag, id -> charges(name.apply(id)), 0, -1);
    }
    private static int charges(String name)
    {
        if (name == null) return 0;
        if ("Gricoller's can".equalsIgnoreCase(name)) return 1000;
        return name.regionMatches(true, 0, "watering can", 0, 12) && CAN.matcher(name).matches()
            ? name.charAt(name.length() - 2) - '0' : 0;
    }

    static boolean waterNeeded(PatchType type,Observation o,boolean enabled)
    {return enabled&&type.canBeWatered()&&liveGrowing(o)&&dryStage(type,o.rawValue);}
    static boolean watered(PatchType type,Observation o)
    {
        if(!liveGrowing(o))return false;
        int raw=o.rawValue;
        // The final allotment stage has a 65-value offset, unlike earlier stages.
        if(type==PatchType.ALLOTMENT&&raw>=127)return between(raw,128,134);
        return dryStage(type,raw-(type==PatchType.HOPS?128:64));
    }
    private static boolean liveGrowing(Observation o)
    {return o!=null&&o.source==Source.LIVE&&o.state==CropState.GROWING;}
    private static boolean dryStage(PatchType type,int r)
    {
        switch(type)
        {
            case ALLOTMENT:return between(r,6,9)||between(r,13,16)||between(r,20,23)||between(r,27,30)||between(r,34,39)||between(r,43,48)||between(r,52,59)||between(r,63,69);
            case FLOWER:return between(r,8,11)||between(r,13,16)||between(r,18,21)||between(r,23,26)||between(r,28,31)||between(r,37,40);
            case HOPS:return between(r,4,7)||between(r,14,18)||between(r,26,31)||between(r,40,46)||between(r,56,63)||between(r,74,77)||between(r,84,88)||between(r,96,98)||between(r,104,107)||between(r,114,118);
            default:return false;
        }
    }
    static boolean needsCure(Observation o,boolean verified)
    {return verified&&o!=null&&o.source==Source.LIVE&&o.state==CropState.DISEASED;}
    static boolean prunes(PatchType type)
    {return type.isTree()||type==PatchType.BUSH||type==PatchType.CORAL;}
    // Optional local watering never gates completion or waits indefinitely.
    static boolean offerExpired(int ticks,int distance){return ticks>=15||ticks<0||distance>5;}
    private static boolean between(int v,int min,int max){return v>=min&&v<=max;}
    private CropCare(){}
}
