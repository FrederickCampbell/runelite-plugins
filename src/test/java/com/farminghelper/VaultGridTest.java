package com.farminghelper;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class VaultGridTest
{
    private static VaultGrid.Slot slot(int key,int x,int y,boolean keep,int rank)
    {return new VaultGrid.Slot(key,x,y,keep,rank);}
    @Test public void flatGridIgnoresCategoriesAndWrapsAtNativeColumns()
    {
        var moves=VaultGrid.flat(List.of(slot(0,14,128,false,99),slot(1,79,278,true,2),
            slot(2,14,428,true,0),slot(3,79,500,true,1)),List.of(14,79),104,66);
        assertEquals(3,moves.size());
        assertEquals(2,moves.get(0).key);assertEquals(14,moves.get(0).x);assertEquals(104,moves.get(0).y);
        assertEquals(3,moves.get(1).key);assertEquals(79,moves.get(1).x);assertEquals(104,moves.get(1).y);
        assertEquals(1,moves.get(2).key);assertEquals(14,moves.get(2).x);assertEquals(170,moves.get(2).y);
    }
    @Test public void nativeSlotsAreReusedWithoutChangingItemIdentity()
    {
        VaultGrid.Section section=new VaultGrid.Section(200,420,List.of(
            slot(0,14,228,false,99),slot(1,79,228,true,2),slot(2,144,228,false,99),
            slot(10,14,300,true,0),slot(12,79,300,true,1)));
        VaultGrid.Layout result=VaultGrid.layout(section,true,0);
        assertEquals(72,result.saved);assertFalse(result.hideHeader);
        assertEquals(3,result.moves.size());
        assertEquals(10,result.moves.get(0).key);assertEquals(14,result.moves.get(0).x);assertEquals(228,result.moves.get(0).y);
        assertEquals(12,result.moves.get(1).key);assertEquals(79,result.moves.get(1).x);
        assertEquals(1,result.moves.get(2).key);assertEquals(144,result.moves.get(2).x);
    }
    @Test public void emptySectionsCanBeRemovedOnlyWithVerifiedHeaderGeometry()
    {
        VaultGrid.Section empty=new VaultGrid.Section(200,350,List.of(slot(5,14,228,false,99)));
        VaultGrid.Layout full=VaultGrid.layout(empty,true,0);
        assertTrue(full.hideHeader);assertEquals(150,full.saved);assertTrue(full.moves.isEmpty());
        VaultGrid.Layout fallback=VaultGrid.layout(empty,false,0);
        assertFalse(fallback.hideHeader);assertEquals(0,fallback.saved);
        VaultGrid.Section next=new VaultGrid.Section(350,500,List.of(slot(7,14,378,true,0)));
        assertEquals(228,VaultGrid.layout(next,true,full.saved).moves.get(0).y);
    }
    @Test public void fallbackCompactsWithinSectionWithoutChangingItsBounds()
    {
        VaultGrid.Section section=new VaultGrid.Section(350,500,List.of(slot(2,14,378,false,99),slot(7,79,378,true,0)));
        VaultGrid.Layout layout=VaultGrid.layout(section,false,0);
        assertEquals(0,layout.saved);assertEquals(14,layout.moves.get(0).x);assertEquals(378,layout.moves.get(0).y);
    }
}
