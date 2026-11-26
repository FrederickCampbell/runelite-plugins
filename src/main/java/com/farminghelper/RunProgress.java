package com.farminghelper;

import java.util.*;
import java.util.function.Predicate;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;

/** Stable run-wide accounting; switching stages does not discard earlier outcomes.
 * Deferred means not currently scheduled, not a verified player action. */
final class RunProgress
{
    enum Status { PENDING, COMPLETED, PRESERVED, SKIPPED, DEFERRED, SEEDLESS, SKIP_ON_UNLOCK }
    private final Map<FarmRunPatch,Status> states=new LinkedHashMap<>();
    void reset(){states.clear();}
    void include(FarmRunPatch patch)
    {
        states.putIfAbsent(patch,Status.PENDING);
        states.replace(patch,Status.SKIP_ON_UNLOCK,Status.SKIPPED);
    }
    boolean is(FarmRunPatch patch,Status status){return states.get(patch)==status;}
    boolean finished(FarmRunPatch patch){return is(patch,Status.COMPLETED)||is(patch,Status.PRESERVED);}
    void finish(FarmRunPatch patch,boolean preserve){states.put(patch,preserve?Status.PRESERVED:Status.COMPLETED);}
    boolean reopenForLiveLoss(FarmRunPatch patch,Observation observation)
    {
        if(observation==null||observation.source!=Source.LIVE||!finished(patch))return false;
        // Cleared/empty completed plots may be intentional (clear-only). Only
        // verified death reopens them; preserved living crops also cannot be empty.
        boolean lost=observation.state==CropState.DEAD||
            is(patch,Status.PRESERVED)&&(observation.state==CropState.EMPTY||observation.state==CropState.STUMP);
        if(lost)states.put(patch,Status.PENDING);
        return lost;
    }
    void skip(FarmRunPatch patch)
    {
        states.compute(patch,(p,old)->old==null||old==Status.SKIP_ON_UNLOCK?Status.SKIP_ON_UNLOCK:Status.SKIPPED);
    }
    void update(Predicate<FarmRunPatch> stage,Collection<FarmRunPatch> ready,Set<FarmRunPatch> seedless)
    {
        Set<FarmRunPatch> actionable=new HashSet<>(ready);
        states.replaceAll((p,old)->{
            if(finished(p)||old==Status.SKIPPED||old==Status.SKIP_ON_UNLOCK||!stage.test(p))return old;
            if(actionable.contains(p))return Status.PENDING;
            return seedless.contains(p)?Status.SEEDLESS:Status.DEFERRED;
        });
    }
    GuideView.Stats stats()
    {
        int[] counts=new int[Status.values().length];
        for(Status state:states.values())counts[state.ordinal()]++;
        return new GuideView.Stats(counts[Status.COMPLETED.ordinal()],counts[Status.SKIPPED.ordinal()],
            counts[Status.DEFERRED.ordinal()],counts[Status.SEEDLESS.ordinal()],states.size()-counts[Status.SKIP_ON_UNLOCK.ordinal()],
            counts[Status.PENDING.ordinal()],counts[Status.PRESERVED.ordinal()]);
    }
}
