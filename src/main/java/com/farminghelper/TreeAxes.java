package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;

/** Rank usable observed axes, excluding broken/unrecognized variants. */
final class TreeAxes
{
    private static final String[] NAMES = {"bronze axe","iron axe","steel axe","black axe","mithril axe","adamant axe","rune axe","dragon axe","crystal axe"};
    private static final int[] LEVELS = {1,1,6,11,21,31,41,61,71};

    static int best(Map<Integer,Integer> stock,int level,IntFunction<String> name)
    {
        return PhysicalStock.best(stock, id -> rank(name.apply(id), level), -1, 0);
    }
    static int rank(String name,int level)
    {
        if(name==null)return -1;
        String n=name.toLowerCase(Locale.ROOT).replace(" felling axe"," axe");
        if(n.equals("3rd age axe")||n.equals("infernal axe"))n="dragon axe";
        for(int i=0;i<NAMES.length;i++)if(n.equals(NAMES[i])&&level>=LEVELS[i])return i;
        return -1;
    }
    private TreeAxes(){}
}
