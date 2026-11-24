package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;

final class CropCare
{
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
    static boolean supportsWater(PatchType type)
    {return type==PatchType.ALLOTMENT||type==PatchType.FLOWER||type==PatchType.HOPS;}
    static boolean waterNeeded(PatchType type,Observation o,int can)
    {
        if(can<=0||!supportsWater(type)||o==null||o.source!=Source.LIVE||o.state!=CropState.GROWING)return false;
        int r=o.rawValue;
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
    {return StepEngine.isTreeType(type)||type==PatchType.BUSH||type==PatchType.CORAL;}
    private static boolean between(int v,int min,int max){return v>=min&&v<=max;}
    private CropCare(){}
}
