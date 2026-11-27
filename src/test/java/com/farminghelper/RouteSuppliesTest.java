package com.farminghelper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RouteSuppliesTest
{
    private static final Map<Integer,String> NAMES=Map.of(1,"Air rune",2,"Earth rune",3,"Law rune",4,"Water rune",5,"Fire rune",6,"Dust rune",7,"Rune pouch",772,"Dramen staff",8,"Staff of earth");
    @Test public void houseThenFairyRingIncludesBankedPouchAndLawRunes()
    {
        var routes=Map.of("A",List.of("Teleport to House",RouteSupplies.FAIRY),"B",List.of("Watchtower Teleport"));
        var plan=RouteSupplies.plan(Set.of("A","B"),routes,Map.of(),Map.of(),Map.of(7,1,772,1,3,10),Map.of(),Map.of(),7,Map.of(1,10,2,10),false,NAMES::get,id->id<7);
        assertEquals(Map.of(7,1,772,1,3,3),plan.quantities);
        assertEquals(Set.of("A","B"),plan.sites.get(3));
    }
    @Test public void eliteAndEquippedStaffAndCombinationRuneAreHandledConservatively()
    {
        var plan=RouteSupplies.plan(Set.of("A"),Map.of("A",List.of("Teleport to House",RouteSupplies.FAIRY)),Map.of(),Map.of(),Map.of(3,1,6,1,8,1),Map.of(8,1),Map.of(8,1),-1,Map.of(),true,NAMES::get,id->id<7);
        assertEquals(Map.of(3,1,6,1),plan.quantities);
    }
    @Test public void unresolvedSpellKeepsFallbackAndWalkingDoesNot()
    {
        var plan=RouteSupplies.plan(Set.of("A","B"),Map.of("A",List.of("Teleport to House"),"B",List.of()),Map.of(10,Set.of("A"),11,Set.of("B")),Map.of(),Map.of(10,1,11,1),Map.of(),Map.of(),-1,Map.of(),false,id->"tablet",id->true);
        assertEquals(Map.of(10,1),plan.quantities);
    }
}
