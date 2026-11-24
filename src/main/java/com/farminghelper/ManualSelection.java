package com.farminghelper;

import java.util.*;
import java.util.function.Predicate;

/** A saved checkbox preference, not a saved guide/session. Keys are physical patches. */
final class ManualSelection
{
    static Set<String> decode(String stored, List<FarmRunPatch> patches)
    {
        Set<String> result=new LinkedHashSet<>();
        Set<String> known=new HashSet<>();
        for(FarmRunPatch p:patches)known.add(p.key());
        if(stored==null)return result;
        for(String key:stored.split("\n"))if(known.contains(key))result.add(key);
        return result;
    }
    static String encode(Set<String> keys){return String.join("\n",new TreeSet<>(keys));}
    static void select(Set<String> keys,List<FarmRunPatch> patches,Predicate<FarmRunPatch> allowed,boolean value)
    {
        for(FarmRunPatch p:patches)if(allowed.test(p))
        {if(value)keys.add(p.key());else keys.remove(p.key());}
    }
    static boolean includes(FarmRunPatch p,Set<FarmRunType> types,Set<String> manual)
    {return types.contains(p.getFarmRunType()) && (manual==null || manual.contains(p.key()));}
}
