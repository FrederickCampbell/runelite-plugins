package com.farminghelper;

import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class Guide15Test
{
    private QuestFarmingStateReader.Observation live(CropState state,Boolean protection)
    { return new QuestFarmingStateReader.Observation(state,QuestFarmingStateReader.Source.LIVE,1,protection,"",1); }
    @Test public void missingPreferredStockStillProducesRequirement()
    {
        FarmRunPatch a=new FarmRunPatch("Falador","",PatchType.HERB), b=new FarmRunPatch("Catherby","",PatchType.HERB);
        Crop ranarr=CropCatalog.findByName(PatchType.HERB,"Ranarr");
        Map<FarmRunPatch,Crop> plan=SeedChoices.allocate(List.of(a,b),72,Map.of(),t->"Ranarr");
        assertEquals(ranarr,plan.get(a));assertEquals(ranarr,plan.get(b));
    }
    @Test public void explicitOverrideReservesAvailableStockBeforeAutomaticChoices()
    {
        FarmRunPatch a=new FarmRunPatch("Falador","",PatchType.HERB), b=new FarmRunPatch("Catherby","",PatchType.HERB);
        Crop ranarr=CropCatalog.findByName(PatchType.HERB,"Ranarr"), snap=CropCatalog.findByName(PatchType.HERB,"Snapdragon");
        Map<FarmRunPatch,Crop> plan=SeedChoices.allocate(List.of(a,b),72,Map.of(ranarr.getItemId(),1,snap.getItemId(),1),t->"Ranarr",p->p==b?"Ranarr":"");
        assertEquals(snap,plan.get(a));assertEquals(ranarr,plan.get(b));
    }
    @Test public void cureDoesNotTriggerProtectionAndPlantingDoes()
    {
        GuideProgress progress=new GuideProgress();progress.observe(live(CropState.DISEASED,false));progress.observe(live(CropState.GROWING,false));
        assertFalse(progress.planted());assertTrue(progress.finished(PatchType.TREE,live(CropState.GROWING,false),true,true));
        progress.reset();progress.observe(live(CropState.EMPTY,false));progress.observe(live(CropState.GROWING,false));
        assertTrue(progress.planted());assertFalse(progress.finished(PatchType.TREE,live(CropState.GROWING,false),true,true));
        assertTrue(progress.finished(PatchType.TREE,live(CropState.GROWING,true),true,true));
        assertTrue(progress.finished(PatchType.TREE,live(CropState.GROWING,false),true,false));
    }
    @Test public void predictionDoesNotConfirmAnInteraction()
    {
        GuideProgress progress=new GuideProgress();progress.observe(live(CropState.EMPTY,false));
        QuestFarmingStateReader.Observation predicted=new QuestFarmingStateReader.Observation(CropState.GROWING,QuestFarmingStateReader.Source.TIME_TRACKING,1,false,"");
        progress.observe(predicted);assertFalse(progress.planted());assertFalse(progress.finished(PatchType.HERB,predicted,false,false));
    }
}
