package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;

/** Rank usable observed axes, excluding broken/unrecognized variants. */
final class TreeAxes
{
    static int best(Map<Integer,Integer> stock,int level,IntFunction<String> name)
    {
        int best=0,score=-1;
        for(int id:new TreeSet<>(stock.keySet()))
        {
            if(stock.get(id)<=0)continue;
            String n=name.apply(id);
            int rank=rank(n,level);
            if(rank>score&&rank>=0){score=rank;best=id;}
        }
        return best;
    }
    static int rank(String name,int level)
    {
        if(name==null)return -1;
        String n=name.toLowerCase(Locale.ROOT).replace(" felling axe"," axe");
        String[] names={"bronze axe","iron axe","steel axe","black axe","mithril axe","adamant axe","rune axe","dragon axe","crystal axe"};
        int[] levels={1,1,6,11,21,31,41,61,71};
        if(n.equals("3rd age axe")||n.equals("infernal axe"))n="dragon axe";
        for(int i=0;i<names.length;i++)if(n.equals(names[i])&&level>=levels[i])return i;
        return -1;
    }
    private TreeAxes(){}
}
