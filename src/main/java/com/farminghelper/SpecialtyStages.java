package com.farminghelper;

import java.util.*;

/** Specialty farms first; birdhouses have their own inventory/bank stage. */
final class SpecialtyStages
{
    static boolean includes(boolean staged,boolean birdPhase,PatchType type)
    {return !staged || (type==PatchType.BIRDHOUSE)==birdPhase;}
    static List<RunOrder.Group> fossilLast(List<RunOrder.Group> groups)
    {
        List<RunOrder.Group> ordered=new ArrayList<>(groups);
        ordered.sort(Comparator.comparingInt(g->"Fossil Island".equals(g.site)?1:0));
        return ordered;
    }
    static Map<Integer,Integer> birdStock(Map<Integer,Integer> bag,Map<Integer,Integer> bank)
    {
        Map<Integer,Integer> result=new HashMap<>(bag);
        bank.forEach((id,n)->result.merge(id,n,Integer::sum));
        return result;
    }
    private SpecialtyStages(){}
}
