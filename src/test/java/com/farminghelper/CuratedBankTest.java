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
    @Test public void nativeVariationsAndChargeSafety()
    {
        assertTrue(CuratedBank.fuzzyEquipment(13579,id->id==11850?"Graceful hood":"Recoloured hood",false));
        assertTrue(CuratedBank.fuzzyEquipment(21253,id->id==13646?"Farmer's strawhat":"Recoloured hat",false));
        assertTrue(CuratedBank.fuzzyEquipment(13579,id->"Graceful hood",true));
        assertFalse(CuratedBank.teleportRows(11105,id->"Skills necklace(4)").isEmpty());
        assertFalse(CuratedBank.teleportRows(11970,id->"Skills necklace(1)").isEmpty());
        assertTrue(CuratedBank.teleportRows(11113,id->"Skills necklace").isEmpty());
        assertTrue(CuratedBank.teleportRows(1704,id->"Amulet of glory").isEmpty());
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
