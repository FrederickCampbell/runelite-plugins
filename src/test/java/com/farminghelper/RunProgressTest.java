package com.farminghelper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RunProgressTest
{
    @Test public void farmingOutcomesRemainAcrossBirdhouseStage()
    {
        FarmRunPatch herb=new FarmRunPatch("Catherby","",PatchType.HERB),flower=new FarmRunPatch("Catherby","",PatchType.FLOWER),tree=new FarmRunPatch("Varrock","",PatchType.TREE),seaweed=new FarmRunPatch("Seaweed","North",PatchType.SEAWEED),bird=BirdhouseRun.patches().get(0);
        RunProgress progress=new RunProgress();
        List.of(herb,flower,tree,seaweed,bird).forEach(progress::include);
        progress.finish(flower,true);progress.skip(tree);
        progress.update(p->p.getPatchType()!=PatchType.BIRDHOUSE,List.of(herb),Set.of(seaweed));
        GuideView.Stats first=progress.stats();
        assertEquals(5,first.total);assertEquals(2,first.pending);assertEquals(1,first.preserved);assertEquals(1,first.skipped);assertEquals(1,first.seedless);assertEquals(0,first.processed);
        progress.finish(herb,false);
        progress.update(p->p.getPatchType()!=PatchType.BIRDHOUSE,List.of(),Set.of(seaweed));
        progress.update(p->p.getPatchType()==PatchType.BIRDHOUSE,List.of(bird),Set.of());
        GuideView.Stats birdPhase=progress.stats();
        assertEquals(5,birdPhase.total);assertEquals(1,birdPhase.pending);assertEquals(1,birdPhase.processed);assertEquals(1,birdPhase.seedless);assertEquals(1,birdPhase.preserved);
        progress.finish(bird,false);
        progress.update(p->p.getPatchType()==PatchType.BIRDHOUSE,List.of(),Set.of());
        GuideView.Stats end=progress.stats();
        assertEquals(0,end.pending);assertEquals(2,end.processed);
        progress.reset();assertEquals(0,progress.stats().total);
    }
    @Test public void deferredIsNotCompletion()
    {
        FarmRunPatch patch=new FarmRunPatch("Kastori","",PatchType.FLOWER);
        RunProgress progress=new RunProgress();progress.include(patch);
        progress.update(p->true,List.of(),Set.of());
        assertEquals(1,progress.stats().deferred);assertEquals(0,progress.stats().processed);
    }
    @Test public void aDeferredPatchCanRejoinButAnExplicitSkipSurvivesUnlock()
    {
        FarmRunPatch flower=new FarmRunPatch("Kastori","",PatchType.FLOWER);
        FarmRunPatch tree=new FarmRunPatch("Varrock","",PatchType.TREE);
        RunProgress progress=new RunProgress();
        progress.include(flower);progress.skip(tree);
        progress.update(p->true,List.of(),Set.of());
        assertEquals(1,progress.stats().total);
        progress.include(tree);
        progress.update(p->true,List.of(tree,flower),Set.of());
        assertEquals(1,progress.stats().skipped);
        assertEquals(1,progress.stats().pending);
        assertEquals(0,progress.stats().deferred);
    }
    @Test public void seedlessModesMapToExistingPlannerFlags()
    {
        assertEquals(SeedlessPolicy.GUIDE,SeedlessPolicy.of(false,true));
        assertEquals(SeedlessPolicy.CLEAR,SeedlessPolicy.of(true,true));
        assertEquals(SeedlessPolicy.SKIP,SeedlessPolicy.of(true,false));
        assertEquals(SeedlessPolicy.GUIDE,SeedlessPolicy.load("invalid"));
    }

    @Test public void onlyVerifiedLossReopensFinishedPlotsAndNeverExplicitSkips()
    {
        var p=new FarmRunPatch("Yanille","",PatchType.HOPS);
        RunProgress progress=new RunProgress();progress.include(p);progress.finish(p,false);
        var dead=new com.farminghelper.queststate.QuestFarmingStateReader.Observation(
            com.farminghelper.queststate.CropState.DEAD,com.farminghelper.queststate.QuestFarmingStateReader.Source.LIVE,1,null,"",220);
        var remembered=new com.farminghelper.queststate.QuestFarmingStateReader.Observation(
            dead.state,com.farminghelper.queststate.QuestFarmingStateReader.Source.TIME_TRACKING,1,null,"",220);
        var empty=new com.farminghelper.queststate.QuestFarmingStateReader.Observation(
            com.farminghelper.queststate.CropState.EMPTY,dead.source,1,null,"",3);
        assertFalse(progress.reopenForLiveLoss(p,remembered));
        assertFalse(progress.reopenForLiveLoss(p,empty));
        assertTrue(progress.reopenForLiveLoss(p,dead));assertEquals(1,progress.stats().pending);
        progress.finish(p,true);assertTrue(progress.reopenForLiveLoss(p,empty));
        progress.skip(p);assertFalse(progress.reopenForLiveLoss(p,dead));
        assertTrue(progress.is(p,RunProgress.Status.SKIPPED));
    }
}
