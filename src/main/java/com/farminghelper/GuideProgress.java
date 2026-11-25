package com.farminghelper;

import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;

/** Only live transitions on the active patch can confirm a player's interaction. */
final class GuideProgress
{
    private boolean sawEmpty, sawWork, planted, changedLive, cured;
    private CropState previous;
    private int previousRaw=-1;
    private Boolean priorProtection;
    private boolean protectionInitiallyKnown;

    void reset() { sawEmpty=false; sawWork=false; planted=false; changedLive=false; cured=false; previous=null; previousRaw=-1; priorProtection=null; protectionInitiallyKnown=false; }
    boolean planted() { return planted; }
    boolean observe(QuestFarmingStateReader.Observation o)
    {
        if (o==null||o.source != QuestFarmingStateReader.Source.LIVE || o.state == null) return false;
        if (!protectionInitiallyKnown) { priorProtection=o.protectedByFarmer; protectionInitiallyKnown=true; }
        if (o.state == CropState.EMPTY) sawEmpty=true;
        if (o.state != CropState.EMPTY && o.state != CropState.GROWING && o.state != CropState.FILLING) sawWork=true;
        if (o.state == CropState.GROWING && previous==CropState.EMPTY && sawEmpty) planted=true;
        if (o.state == CropState.GROWING && previous==CropState.DISEASED) cured=true;
        boolean changed=previous!=null && (previous!=o.state || previousRaw!=o.rawValue);
        changedLive|=changed;
        previous=o.state;previousRaw=o.rawValue;
        return changed;
    }
    boolean finished(PatchType type, QuestFarmingStateReader.Observation o, boolean protectionWanted, boolean paymentCarried)
    {
        return finished(type,o,protectionWanted,paymentCarried,type==PatchType.SPIRIT_TREE||type==PatchType.REDWOOD||type==PatchType.BUSH||type==PatchType.CACTUS);
    }
    boolean finished(PatchType type, QuestFarmingStateReader.Observation o, boolean protectionWanted, boolean paymentCarried,boolean preserve)
    {
        if (o==null||o.source != QuestFarmingStateReader.Source.LIVE) return false;
        if (o.state != CropState.GROWING && o.state != CropState.FILLING) return false;
        if (planted && protectionWanted && paymentCarried && !protectionConfirmed(o)) return false;
        // A replacement crop requires an observed empty -> growing transition.
        // Permanent crops may finish a health check/harvest without replanting.
        boolean perennial=type==PatchType.SPIRIT_TREE||preserve;
        return planted || cured || (perennial && sawWork && changedLive);
    }
    boolean protectionConfirmed(QuestFarmingStateReader.Observation o)
    { return Boolean.TRUE.equals(o.protectedByFarmer) && !Boolean.TRUE.equals(priorProtection); }
}
