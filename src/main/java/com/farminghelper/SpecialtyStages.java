package com.farminghelper;

import java.util.*;

/** Mixed farming runs finish on Fossil Island; birds have their own supply stage. */
final class SpecialtyStages
{
    static boolean includes(boolean staged,boolean birdPhase,PatchType type)
    {return !staged || (type==PatchType.BIRDHOUSE)==birdPhase;}
    static boolean fossil(FarmRunPatch patch){return "Fossil Island".equals(RunOrder.site(patch));}
    static boolean stageBirds(String policy,Set<FarmRunType> types)
    {return "group_SPECIALTY".equals(policy)||("manual".equals(policy)&&types.contains(FarmRunType.BIRDHOUSE)&&types.size()>1);}
    static boolean fossilLastRequired(String policy){return "manual".equals(policy)||"group_SPECIALTY".equals(policy);}
    static int fossilOrder(FarmRunPatch patch)
    {switch(patch.getPatchType()){case HARDWOOD_TREE:return 0;case SEAWEED:return 1;case BIRDHOUSE:return 2;default:return 0;}}
    static List<RunOrder.Group> fossilLast(List<RunOrder.Group> groups)
    {
        List<RunOrder.Group> ordered=new ArrayList<>(groups);
        ordered.sort(Comparator.comparingInt(g->"Fossil Island".equals(g.site)?1:0));
        for(int i=0;i<ordered.size();i++)
        {
            RunOrder.Group g=ordered.get(i);if(!"Fossil Island".equals(g.site))continue;
            List<FarmRunPatch> patches=new ArrayList<>(g.patches);
            patches.sort(Comparator.comparingInt(SpecialtyStages::fossilOrder).thenComparingInt(p->p.getPatchType()==PatchType.BIRDHOUSE?BirdhouseRun.order(p):0));
            if(!patches.equals(g.patches))ordered.set(i,new RunOrder.Group(g.site,patches));
        }
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
