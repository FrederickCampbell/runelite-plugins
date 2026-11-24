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
        progress.include(List.of(herb,flower,tree,seaweed,bird));
        progress.update(p->p.getPatchType()!=PatchType.BIRDHOUSE,List.of(herb),Set.of(flower),Set.of(flower),Set.of(tree),Set.of(seaweed));
        GuideView.Stats first=progress.stats();
        assertEquals(5,first.total);assertEquals(2,first.pending);assertEquals(1,first.preserved);assertEquals(1,first.skipped);assertEquals(1,first.seedless);assertEquals(0,first.processed);
        progress.update(p->p.getPatchType()!=PatchType.BIRDHOUSE,List.of(),Set.of(herb,flower),Set.of(flower),Set.of(tree),Set.of(seaweed));
        progress.update(p->p.getPatchType()==PatchType.BIRDHOUSE,List.of(bird),Set.of(herb,flower),Set.of(flower),Set.of(tree),Set.of());
        GuideView.Stats birdPhase=progress.stats();
        assertEquals(5,birdPhase.total);assertEquals(1,birdPhase.pending);assertEquals(1,birdPhase.processed);assertEquals(1,birdPhase.seedless);assertEquals(1,birdPhase.preserved);
        progress.update(p->p.getPatchType()==PatchType.BIRDHOUSE,List.of(),Set.of(herb,flower,bird),Set.of(flower),Set.of(tree),Set.of());
        GuideView.Stats end=progress.stats();
        assertEquals(0,end.pending);assertEquals(2,end.processed);
        progress.reset();assertEquals(0,progress.stats().total);
    }
    @Test public void deferredIsNotCompletion()
    {
        FarmRunPatch patch=new FarmRunPatch("Kastori","",PatchType.FLOWER);
        RunProgress progress=new RunProgress();progress.include(List.of(patch));
        progress.update(p->true,List.of(),Set.of(),Set.of(),Set.of(),Set.of());
        assertEquals(1,progress.stats().deferred);assertEquals(0,progress.stats().processed);
    }
    @Test public void seedlessModesMapToExistingPlannerFlags()
    {
        assertEquals(SeedlessPolicy.GUIDE,SeedlessPolicy.of(false,true));
        assertEquals(SeedlessPolicy.CLEAR,SeedlessPolicy.of(true,true));
        assertEquals(SeedlessPolicy.SKIP,SeedlessPolicy.of(true,false));
        assertEquals(SeedlessPolicy.GUIDE,SeedlessPolicy.load("invalid"));
    }
}
