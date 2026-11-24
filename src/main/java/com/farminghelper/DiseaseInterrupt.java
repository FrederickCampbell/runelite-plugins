package com.farminghelper;

import java.util.*;
import java.util.function.ToIntFunction;

/** Independent priority state. The ordinary guide cursor and progress are never replaced. */
final class DiseaseInterrupt
{
    private FarmRunPatch active;
    private final Set<FarmRunPatch> dismissed=new HashSet<>();
    FarmRunPatch select(Collection<FarmRunPatch> verified,ToIntFunction<FarmRunPatch> distance)
    {
        if(active!=null&&verified.contains(active)&&!dismissed.contains(active))return active;
        active=verified.stream().filter(p->!dismissed.contains(p))
            .min(Comparator.comparingInt(distance).thenComparing(FarmRunPatch::key)).orElse(null);
        return active;
    }
    void skip(FarmRunPatch patch){if(patch!=null&&patch==active){dismissed.add(patch);active=null;}}
    void reset(){active=null;dismissed.clear();}
}
