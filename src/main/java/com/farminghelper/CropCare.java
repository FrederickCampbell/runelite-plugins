package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;

final class CropCare
{
    // Native farming_tools_getstored uses varbit 1439 and enum 136.
    static int storedCan(net.runelite.api.Client client,IntFunction<String> name)
    {
        int key=client.getVarbitValue(1439);
        if(key<=0)return -1;
        var variants=client.getEnum(136);
        if(variants==null)return -1;
        int id=variants.getIntValue(key);
        return id>0?wateringCan(Map.of(id,1),name):-1;
    }
    static int preferredCan(int carried,int stored,int bank)
    {return carried>0?carried:stored>0?stored:bank>0?bank:5333;}
    static int wateringCan(Map<Integer,Integer> bag,IntFunction<String> name)
    {
        int best=-1,charges=0;
        for(int id:new TreeSet<>(bag.keySet()))
        {
            if(bag.getOrDefault(id,0)<=0)continue;
            String n=name.apply(id);if(n==null)continue;
            int c=0;
            if(n.equalsIgnoreCase("Gricoller's can"))c=1000;
            else if(n.matches("(?i)watering can\\([1-8]\\)"))c=n.charAt(n.length()-2)-'0';
            if(c>charges){best=id;charges=c;}
        }
        return best;
    }
    static boolean waterNeeded(PatchType type,Observation o,boolean enabled)
    {
        if(!enabled||!type.canBeWatered()||o==null||o.source!=Source.LIVE||o.state!=CropState.GROWING)return false;
        int r=o.rawValue;
        switch(type)
        {
            case ALLOTMENT:return between(r,6,9)||between(r,13,16)||between(r,20,23)||between(r,27,30)||between(r,34,39)||between(r,43,48)||between(r,52,59)||between(r,63,69);
            case FLOWER:return between(r,8,11)||between(r,13,16)||between(r,18,21)||between(r,23,26)||between(r,28,31)||between(r,37,40);
            case HOPS:return between(r,4,7)||between(r,14,18)||between(r,26,31)||between(r,40,46)||between(r,56,63)||between(r,74,77)||between(r,84,88)||between(r,96,98)||between(r,104,107)||between(r,114,118);
            default:return false;
        }
    }
    static boolean watered(PatchType type,Observation o)
    {
        if(o==null||o.source!=Source.LIVE||o.state!=CropState.GROWING)return false;
        int r=o.rawValue;
        switch(type)
        {
            case ALLOTMENT:return between(r,70,73)||between(r,77,80)||between(r,84,87)||between(r,91,94)||between(r,98,103)||between(r,107,112)||between(r,116,123)||between(r,128,134);
            case FLOWER:return between(r,72,75)||between(r,77,80)||between(r,82,85)||between(r,87,90)||between(r,92,95)||between(r,101,104);
            case HOPS:return between(r,132,135)||between(r,142,146)||between(r,154,159)||between(r,168,174)||between(r,184,191)||between(r,202,205)||between(r,212,216)||between(r,224,226)||between(r,232,235)||between(r,242,246);
            default:return false;
        }
    }
    static boolean needsCure(Observation o,boolean verified)
    {return verified&&o!=null&&o.source==Source.LIVE&&o.state==CropState.DISEASED;}
    static boolean prunes(PatchType type)
    {return type.isTree()||type==PatchType.BUSH||type==PatchType.CORAL;}
    private static boolean between(int v,int min,int max){return v>=min&&v<=max;}
    private CropCare(){}
}
