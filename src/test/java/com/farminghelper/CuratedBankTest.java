package com.farminghelper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class CuratedBankTest
{
    @Test public void shelfIsNotLoadout()
    {
        assertTrue(CuratedBank.equipment("Graceful hood",false));
        assertTrue(CuratedBank.equipment("Tome of fire",true));
        assertTrue(CuratedBank.equipment("Fishbowl helmet",false));
        assertFalse(CuratedBank.equipment("Fishbowl helmet",true));
        assertFalse(CuratedBank.equipment("Dragon claws",false));
    }
    @Test public void restrictionsAndModes()
    {
        assertEquals(Set.of(4251,13393),CuratedBank.blocked("4251, 13393@42, 11105:5"));
        assertFalse(CuratedBank.modeAllows("None",false));
        assertFalse(CuratedBank.modeAllows("Inventory (perm)",true));
        assertTrue(CuratedBank.modeAllows("Inventory and Bank",true));
    }
    @Test public void coralAlternatives()
    {
        Map<Integer,Integer> kit=new HashMap<>();
        DivingKit.require(kit,Map.of(DivingKit.MEDALLION,1),Map.of());
        assertEquals(Map.of(DivingKit.MEDALLION,1),kit);
        kit.clear();DivingKit.require(kit,Map.of(),Map.of());
        assertEquals(Map.of(DivingKit.HELMET,1,DivingKit.APPARATUS,1),kit);
    }
}
