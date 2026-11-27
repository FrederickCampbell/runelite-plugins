package com.farminghelper;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;

public class LocalWorkflowTest
{
    private static FarmRunPatch p(String site,PatchType type){return new FarmRunPatch(site,"",type);}
    @Test public void nearbyVisitWinsWithoutAbandoningActivePlot()
    {
        var a=p("Catherby",PatchType.HERB);var b=p("Catherby",PatchType.ALLOTMENT);var c=p("Falador",PatchType.HERB);
        var ready=new ArrayList<>(List.of(a,b,c));var visible=new ArrayList<>(ready);
        var distances=Map.of(a,12,b,3,c,100);
        RunOrder.preferLocal(ready,visible,a,false,distances::get);
        assertEquals(List.of(b,a,c),ready);
        RunOrder.preferLocal(ready,visible,b,true,x->x==a?1:10);
        assertSame(b,ready.get(0));
        ready=new ArrayList<>(List.of(c,a,b));visible=new ArrayList<>(ready);
        RunOrder.preferLocal(ready,visible,c,false,distances::get);
        assertSame(b,ready.get(0));
    }
    @Test public void finishesCurrentVisitBeforeLeavingForRemotePlot()
    {
        var done=p("Catherby",PatchType.HERB);var local=p("Catherby",PatchType.ALLOTMENT);var remote=p("Falador",PatchType.HERB);
        var ready=new ArrayList<>(List.of(remote,local));
        RunOrder.preferLocal(ready,new ArrayList<>(ready),done,false,x->x==local?12:100);
        assertSame(local,ready.get(0));
    }
    @Test public void outOfOrderPlantingRequiresLiveEvidenceAndStillRequiresProtection()
    {
        var tracker=new GuideProgress();
        var empty=new com.farminghelper.queststate.QuestFarmingStateReader.Observation(
            com.farminghelper.queststate.CropState.EMPTY,com.farminghelper.queststate.QuestFarmingStateReader.Source.LIVE,1,false,"",3);
        var grown=new com.farminghelper.queststate.QuestFarmingStateReader.Observation(
            com.farminghelper.queststate.CropState.GROWING,com.farminghelper.queststate.QuestFarmingStateReader.Source.LIVE,2,false,"",4);
        tracker.observe(empty);assertFalse(tracker.underway());
        tracker.observe(grown);assertTrue(tracker.planted());assertTrue(tracker.underway());
        assertTrue(tracker.finished(PatchType.ALLOTMENT,grown,false,false));
        assertFalse(tracker.finished(PatchType.ALLOTMENT,grown,true,true));
        tracker.reset();tracker.observe(grown);assertFalse(tracker.planted());
    }
    @Test public void bucketsAreOnlyDepositedWhenSpareAndStorageHasSpace()
    {
        int bucket=CompostRun.BUCKET;
        assertEquals(5,HarvestSpace.depositBuckets(Map.of(bucket,5),Map.of(),0,0,0));
        assertEquals(0,HarvestSpace.depositBuckets(Map.of(bucket,5),Map.of(bucket,1),0,0,0));
        assertEquals(1,HarvestSpace.depositBuckets(Map.of(bucket,5),Map.of(),7,7,3));
    }
    @Test public void terminalAndSpecialRoutesAreNotPromoted()
    {
        var a=p("Catherby",PatchType.HERB);var b=p("Seaweed",PatchType.SEAWEED);
        var ready=new ArrayList<>(List.of(a,b));
        RunOrder.preferLocal(ready,new ArrayList<>(ready),null,false,x->x==b?1:99);
        assertSame(a,ready.get(0));
        ready=new ArrayList<>(List.of(b,a));
        RunOrder.preferLocal(ready,new ArrayList<>(ready),null,false,x->x==a?1:99);
        assertSame(b,ready.get(0));
    }
    @Test public void collectionDeduplicatesAndRespectsSlots()
    {
        var rake=LeprechaunWithdrawal.plan(ItemID.RAKE,false,1,2,"Rake");
        var spade=LeprechaunWithdrawal.plan(ItemID.SPADE,false,1,2,"Spade");
        var dibber=LeprechaunWithdrawal.plan(ItemID.DIBBER,false,1,2,"Dibber");
        var plan=LeprechaunWithdrawal.append(rake,spade,2);
        assertSame(plan,LeprechaunWithdrawal.append(plan,spade,3));
        assertSame(plan,LeprechaunWithdrawal.append(plan,dibber,2));
        var three=LeprechaunWithdrawal.append(plan,dibber,3);
        assertEquals("Dibber",three.companion.companion.name);
        assertNull(three.companion.companion.companion);
        var full=LeprechaunWithdrawal.plan(ItemID.RAKE,false,1,0,"Rake");
        assertSame(full,LeprechaunWithdrawal.append(full,spade,3));
    }
    @Test public void compostCollectionRespectsTotalSlotsStorageAndExistingPlan()
    {
        int ultra=ItemID.BUCKET_ULTRACOMPOST;
        var rake=LeprechaunWithdrawal.plan(ItemID.RAKE,false,1,4,"Rake");
        var plan=LeprechaunWithdrawal.withCompost(rake,ultra,4,10,3,"Ultracompost");
        assertEquals(2,plan.companion.quantity);
        assertEquals("Remove 2 total",plan.companion.label());
        assertTrue(plan.instruction().contains("2 × Ultracompost"));
        assertSame(plan,LeprechaunWithdrawal.withCompost(plan,ultra,4,10,3,"Ultracompost"));
        assertEquals(3,LeprechaunWithdrawal.withCompost(plan,ultra,4,10,4,"Ultracompost").companion.quantity);
        assertEquals(1,LeprechaunWithdrawal.withCompost(null,ultra,4,1,5,"Ultracompost").quantity);
        assertNull(LeprechaunWithdrawal.withCompost(null,ultra,4,10,0,"Ultracompost"));
    }
    @Test public void onlyNoteKnownUnreservedProduceAndRespectFaladorCabbage()
    {
        int cabbage=ItemID.CABBAGE,melon=ItemID.WATERMELON;
        ItemComposition a=mock(ItemComposition.class),b=mock(ItemComposition.class);
        when(a.getName()).thenReturn("Cabbage");when(b.getName()).thenReturn("Watermelon");
        when(a.getNote()).thenReturn(-1);when(b.getNote()).thenReturn(-1);
        when(a.getLinkedNoteId()).thenReturn(cabbage+1);when(b.getLinkedNoteId()).thenReturn(melon+1);
        var defs=Map.of(cabbage,a,melon,b);
        var bag=Map.of(cabbage,8,melon,5);
        assertEquals(cabbage,HarvestSpace.best(bag,Map.of(),"Catherby",defs::get));
        assertEquals(melon,HarvestSpace.best(bag,Map.of(),"Falador",defs::get));
        assertEquals(-1,HarvestSpace.best(bag,Map.of(melon,1),"Falador",defs::get));
        assertEquals(-1,HarvestSpace.best(Map.of(melon,1),Map.of(),"Catherby",defs::get));
        assertEquals(melon,HarvestSpace.best(Map.of(melon,1,melon+1,8),Map.of(),"Catherby",defs::get));
    }
}
