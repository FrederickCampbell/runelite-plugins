package com.farminghelper;

import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;

/** Only live transitions on the active patch can confirm a player's interaction. */
final class GuideProgress
{
    private boolean sawEmpty, sawWork, planted;
    private CropState previous;
    private int previousRaw=-1;
    private Boolean priorProtection;
    private boolean protectionInitiallyKnown;

    void reset() { sawEmpty=false; sawWork=false; planted=false; previous=null; previousRaw=-1; priorProtection=null; protectionInitiallyKnown=false; }
    boolean planted() { return planted; }
    boolean observe(QuestFarmingStateReader.Observation o)
    {
        if (o.source != QuestFarmingStateReader.Source.LIVE || o.state == null) return false;
        if (!protectionInitiallyKnown) { priorProtection=o.protectedByFarmer; protectionInitiallyKnown=true; }
        if (o.state == CropState.EMPTY) sawEmpty=true;
        if (o.state != CropState.EMPTY && o.state != CropState.GROWING && o.state != CropState.FILLING) sawWork=true;
        if (o.state == CropState.GROWING && sawEmpty) planted=true;
        boolean changed=previous!=null && (previous!=o.state || previousRaw!=o.rawValue);
        previous=o.state;previousRaw=o.rawValue;
        return changed;
    }
    boolean finished(PatchType type, QuestFarmingStateReader.Observation o, boolean protectionWanted, boolean paymentCarried)
    {
        if (o.source != QuestFarmingStateReader.Source.LIVE) return false;
        if (o.state != CropState.GROWING && o.state != CropState.FILLING) return false;
        if (planted && protectionWanted && paymentCarried && !protectionConfirmed(o)) return false;
        return planted || sawWork;
    }
    boolean protectionConfirmed(QuestFarmingStateReader.Observation o)
    { return Boolean.TRUE.equals(o.protectedByFarmer) && !Boolean.TRUE.equals(priorProtection); }
}
