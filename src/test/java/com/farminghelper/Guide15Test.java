package com.farminghelper;

import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;
import java.util.*;
import org.junit.Test;
import net.runelite.api.coords.WorldPoint;
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
    @Test public void specialtyRoutesUseSurfaceApproachesAndStopAtHespori()
    {
        WorldPoint outside=new WorldPoint(3200,3200,0);
        FarmRunPatch hespori=new FarmRunPatch("Farming Guild","Hespori",PatchType.HESPORI);
        FarmRunPatch seaweed=new FarmRunPatch("Seaweed","North",PatchType.SEAWEED);
        FarmRunPatch coral=new FarmRunPatch("Great Conch","East",PatchType.CORAL);
        assertEquals(new WorldPoint(1230,3730,0),Waypoints.navigationTarget(hespori,outside));
        assertNull(Waypoints.navigationTarget(hespori,new WorldPoint((5021>>8)*64+32,(5021&255)*64+32,0)));
        assertEquals(new WorldPoint(3764,3899,0),Waypoints.navigationTarget(seaweed,outside));
        assertEquals(new WorldPoint(3271,2463,0),Waypoints.navigationTarget(coral,outside));
        assertEquals(Waypoints.destination(seaweed),Waypoints.navigationTarget(seaweed,Waypoints.destination(seaweed)));
        assertEquals(Waypoints.destination(coral),Waypoints.navigationTarget(coral,Waypoints.destination(coral)));
        assertFalse(PatchType.CORAL.usesCompost());
    }
}
