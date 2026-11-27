package com.farminghelper;

import java.util.*;
import java.util.function.IntPredicate;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import net.runelite.api.ItemComposition;

public class AdaptivePreparationTest
{
    private static final int PAYMENT=100, NOTE=101, SAPLING=200, SEED=300, BOX=400;
    private static final IntPredicate STACK=id->id==NOTE||id==SEED||id==500;
    private static FarmRunPatch p(String site,PatchType type){return new FarmRunPatch(site,"",type);}

    @Test public void notedPaymentsAreActionSpecificAndReserveMixedForms()
    {
        ItemComposition item=mock(ItemComposition.class);
        when(item.getNote()).thenReturn(-1);
        when(item.getLinkedNoteId()).thenReturn(NOTE);
        Map<Integer,ItemComposition> definitions=Map.of(PAYMENT,item);
        ProtectionPayment cost=new ProtectionPayment("Coconut",PAYMENT,25);
        assertTrue(ProtectionPayment.carried(cost,Map.of(NOTE,25),definitions::get));
        assertTrue(ProtectionPayment.carried(cost,Map.of(NOTE,20,PAYMENT,5),definitions::get));
        assertFalse(ProtectionPayment.carried(cost,Map.of(NOTE,24),definitions::get));
        assertFalse(ProtectionPayment.carried(cost,Map.of(NOTE,25),id->null));
        Map<Integer,Integer> req=new LinkedHashMap<>(),sources=new HashMap<>();
        req.put(PAYMENT,3); // physical produce reserved for a different interaction
        ProtectionPayment.reserve(req,Map.of(PAYMENT,25),Map.of(PAYMENT,5,NOTE,10),definitions::get,sources);
        assertEquals(Map.of(PAYMENT,5,NOTE,23),req);
        assertEquals(Map.of(NOTE,PAYMENT),sources);
        assertEquals(26,Preparation.loadedSlots(Map.of(),Map.of(PAYMENT,25,SEED,1),Map.of(),Map.of(),STACK));
        assertEquals(2,Preparation.loadedSlots(Map.of(),Map.of(NOTE,25,SEED,1),Map.of(),Map.of(),STACK));
        assertEquals(1,Preparation.loadedSlots(Map.of(),Map.of(SAPLING,1),Map.of(),Map.of(NOTE,99),STACK));
    }

    @Test public void actualInventoryEquipmentAndConfirmedBoxCount()
    {
        Map<Integer,Integer> bag=Map.of(999,10,BOX,1,SEED,1);
        Map<Integer,Integer> req=Map.of(SAPLING,9,SEED,5,501,1,500,6);
        assertEquals(23,Preparation.loadedSlots(bag,req,Map.of(),Map.of(),STACK));
        assertEquals(22,Preparation.loadedSlots(bag,req,Map.of(501,1),Map.of(SEED,4),STACK));
        assertEquals(13,Preparation.loadedSlots(Map.of(999,10,BOX,1),Map.of(SEED,5,SAPLING,1),Map.of(),Map.of(),STACK));
        assertEquals(12,Preparation.loadedSlots(Map.of(999,10,BOX,1),Map.of(SEED,5,SAPLING,1),Map.of(),Map.of(SEED,5),STACK));
    }

    @Test public void oversizedCustomRunProducesBoundedPrefix()
    {
        Preparation.Window window=new Preparation.Window(60);
        int probes=0;
        do {probes++;assertTrue(probes<=15);}
        while(window.advance(Preparation.loadedSlots(Map.of(999,5),Map.of(SAPLING,window.limit()),Map.of(),Map.of(),STACK)<=26));
        assertEquals(21,window.limit());
        Preparation.Window blocked=new Preparation.Window(60);
        do {assertTrue(blocked.limit()>0);}while(blocked.advance(false));
        assertEquals(1,blocked.limit());
        Preparation.Window shortRun=new Preparation.Window(8);
        assertFalse(shortRun.advance(true));
        assertEquals(8,shortRun.limit());
    }

    @Test public void guildBoundaryIsOptionalAndDoesNotDeleteTheRun()
    {
        Preparation.Window next=new Preparation.Window(36);
        do {}while(next.advance(next.limit()<=20));
        assertEquals(20,next.limit());
        assertEquals(4,Preparation.workingSlots(List.of(p("Farming Guild",PatchType.ALLOTMENT),p("Farming Guild",PatchType.TREE))));
        assertEquals(5,Preparation.workingSlots(List.of(p("Fossil Island",PatchType.BIRDHOUSE))));
        assertEquals(0,Preparation.workingSlots(List.of(p("Farming Guild",PatchType.COMPOST_BIN))));
    }

    @Test public void transportationCountsAndInsufficientStock()
    {
        assertEquals(2,RunTeleports.chargedQuantity(5,"Skills necklace(4)"));
        assertEquals(4,RunTeleports.chargedQuantity(4,"Unknown charge"));
        List<FarmRunPatch> route=List.of(p("Falador",PatchType.TREE),p("Falador",PatchType.HERB));
        Map<Integer,List<String>> options=Map.of(500,List.of("Falador tablet"),501,List.of("Explorer's ring: Teleport"));
        Map<Integer,Set<String>> selected=RunTeleports.select(route,options,Map.of(500,1),Map.of(500,1,501,1),(id,n)->n);
        assertEquals(Map.of(500,Set.of("Falador Park"),501,Set.of("South Falador")),selected);
        assertEquals(2,Preparation.loadedSlots(Map.of(),Map.of(500,2,501,1),Map.of(),Map.of(),STACK));
    }

    @Test public void largeCompostBinLoadsOnlyWhatFits()
    {
        Map<Integer,Integer> req=new HashMap<>(Map.of(CompostRun.WATERMELON,30));
        CompostRun.batch(req,Map.of(999,4),4,id->false);
        assertEquals(24,(int)req.get(CompostRun.WATERMELON));
        Map<Integer,Integer> collect=new HashMap<>(Map.of(CompostRun.BUCKET,28,CompostRun.ASH,50));
        CompostRun.batch(collect,Map.of(999,2),2,id->id==CompostRun.ASH);
        assertEquals(25,(int)collect.get(CompostRun.BUCKET));
        assertEquals(50,(int)collect.get(CompostRun.ASH));
        assertEquals(28,Preparation.loadedSlots(Map.of(999,2),collect,Map.of(),Map.of(),id->id==CompostRun.ASH));
    }

    @Test public void compostBatchLeavesRoomForUncarriedTransportButNotWornItems()
    {
        Map<Integer,Integer> req=new HashMap<>(Map.of(CompostRun.PINEAPPLE,30,500,1,501,1));
        CompostRun.batch(req,Map.of(999,2),Map.of(501,1),Map.of(),2,STACK);
        assertEquals(25,(int)req.get(CompostRun.PINEAPPLE));
        assertEquals(28,Preparation.loadedSlots(Map.of(999,2),req,Map.of(501,1),Map.of(),STACK));
    }
    @Test public void repeatedPrefixesReuseInventoryObservationWithoutChangingFit()
    {
        Map<Integer,Integer> bag=Map.of(999,12,BOX,1,SEED,8,NOTE,50);
        Map<Integer,Integer> equipped=Map.of(501,1),box=Map.of(SEED,4);
        int[] reads={0};
        IntPredicate stack=id->{reads[0]++;return STACK.test(id);};
        List<Map<Integer,Integer>> demands=List.of(Map.of(SAPLING,22,SEED,13,501,1),
            Map.of(SAPLING,11,SEED,13),Map.of(SAPLING,5,NOTE,70));
        int[] expected=new int[demands.size()];
        for(int i=0;i<demands.size();i++)expected[i]=Preparation.loadedSlots(bag,demands.get(i),equipped,box,stack);
        int repeated=reads[0];reads[0]=0;
        int occupied=Preparation.slots(bag,Map.of(),stack);
        for(int i=0;i<demands.size();i++)assertEquals(expected[i],Preparation.loadedSlots(occupied,bag,demands.get(i),equipped,box,stack));
        assertTrue(reads[0]<repeated);
        assertEquals(repeated-2*bag.size(),reads[0]);
    }
    @Test public void missingNotedPaymentHighlightsBothPhysicalFormsOnlyWhenMissing()
    {
        assertEquals(Set.of(PAYMENT,NOTE),GuideView.missingItems(Map.of(NOTE,25),Map.of(NOTE,10),Map.of(NOTE,PAYMENT)));
        assertTrue(GuideView.missingItems(Map.of(NOTE,25),Map.of(NOTE,25),Map.of(NOTE,PAYMENT)).isEmpty());
        assertEquals(Set.of(SAPLING),GuideView.missingItems(Map.of(SAPLING,2),Map.of(SAPLING,1),Map.of(NOTE,PAYMENT)));
    }
}
