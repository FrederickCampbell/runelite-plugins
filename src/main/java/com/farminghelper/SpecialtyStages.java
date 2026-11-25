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
    private SpecialtyStages(){}
}
