package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;

/** Presentation only. Never changes the allocation, route or requirement quantities. */
final class SupplyOrder
{
    static boolean planting(int id,IntFunction<String> names)
    {
        return VaultRanking.resolve(id,names.apply(id))!=null;
    }

    static boolean tool(String name)
    {
        String n=Objects.toString(name,"").toLowerCase(Locale.ROOT);
        return n.equals("rake")||n.equals("spade")||n.equals("seed dibber")||n.equals("gardening trowel")
            ||n.equals("secateurs")||n.equals("magic secateurs")||n.startsWith("watering can")
            ||n.equals("gricoller's can")||n.endsWith(" axe")||n.equals("hammer")||n.equals("chisel")
            ||n.contains("bottomless compost bucket");
    }

    static int rank(int id,IntFunction<String> names)
    {
        if(planting(id,names))return 0;
        String name=Objects.toString(names.apply(id),"").toLowerCase(Locale.ROOT);
        if(tool(name))return 3;
        if(name.contains("compost")||name.equals("plant cure"))return 1;
        return 2;
    }

    static Map<Integer,Integer> plantingRequirements(Map<Integer,Integer> required,IntFunction<String> names)
    {
        Map<Integer,Integer> result=new LinkedHashMap<>();
        required.forEach((id,n)->{if(planting(id,names))result.put(id,n);});
        return result;
    }

    static List<Integer> ordered(Map<Integer,Integer> required,IntFunction<String> names)
    {
        List<Integer> result=new ArrayList<>(required.keySet());
        result.sort(Comparator.comparingInt(id->rank(id,names)));
        return result;
    }
    private SupplyOrder(){}
}
