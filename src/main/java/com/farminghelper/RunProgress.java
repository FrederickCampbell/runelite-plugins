package com.farminghelper;

import java.util.*;
import java.util.function.Predicate;

/** Stable run-wide accounting; switching stages does not discard earlier outcomes.
 * Deferred means not currently scheduled, not a verified player action. */
final class RunProgress
{
    enum Status {PENDING, COMPLETED, PRESERVED, SKIPPED, DEFERRED, SEEDLESS}
    private final Map<FarmRunPatch,Status> states=new LinkedHashMap<>();
    void reset(){states.clear();}
    void include(Collection<FarmRunPatch> selected){for(FarmRunPatch p:selected)states.putIfAbsent(p,Status.PENDING);}
    void update(Predicate<FarmRunPatch> stage,Collection<FarmRunPatch> ready,Set<FarmRunPatch> completed,Set<FarmRunPatch> preserved,Set<FarmRunPatch> skipped,Set<FarmRunPatch> seedless)
    {
        states.replaceAll((p,old)->{
            if(skipped.contains(p))return Status.SKIPPED;
            if(preserved.contains(p))return Status.PRESERVED;
            if(completed.contains(p))return Status.COMPLETED;
            if(!stage.test(p))return old;
            if(ready.contains(p))return Status.PENDING;
            if(seedless.contains(p))return Status.SEEDLESS;
            return Status.DEFERRED;
        });
    }
    private int count(Status status){return (int)states.values().stream().filter(s->s==status).count();}
    GuideView.Stats stats(){return new GuideView.Stats(count(Status.COMPLETED),count(Status.SKIPPED),count(Status.DEFERRED),count(Status.SEEDLESS),states.size(),count(Status.PENDING),count(Status.PRESERVED));}
}
