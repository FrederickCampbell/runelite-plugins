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
    }
}
