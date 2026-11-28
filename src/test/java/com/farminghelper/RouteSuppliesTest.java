package com.farminghelper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RouteSuppliesTest
{
    private static final Map<Integer,String> NAMES=Map.of(1,"Air rune",2,"Earth rune",3,"Law rune",4,"Water rune",5,"Fire rune",6,"Dust rune",7,"Rune pouch",772,"Dramen staff",8,"Staff of earth");
    @Test public void camelotUsesBankedPouchBeforeWithdrawalWithoutDuplicateLooseRunes()
    {
        var sites=Set.of("Catherby");
        var contents=Map.of(1,100,2,100,3,100);
        var plan=RouteSupplies.plan(sites,Map.of("Catherby",List.of("  Camelot   Teleport ")),Map.of(),Map.of(),Map.of(7,1,1,100,3,100),Map.of(),Map.of(),7,contents,Map.of(1,100,3,100),false,NAMES::get,id->id<7);
        assertEquals(Map.of(7,1),plan.quantities);
        assertEquals(Set.of("Catherby"),plan.sites.get(7));
        assertTrue(RouteSupplies.optionalPouch(sites,Map.of(),7,contents,NAMES::get));
        assertFalse(RouteSupplies.optionalPouch(sites,Map.of("Catherby",List.of()),7,contents,NAMES::get));
        assertFalse(RouteSupplies.optionalPouch(sites,Map.of(),7,Map.of(2,100),NAMES::get));
    }
    @Test public void ownedElementalStaffCanCoverMissingRunesWithoutDuplicatingEquipment()
    {
        var plan=RouteSupplies.plan(new LinkedHashSet<>(List.of("A","B")),Map.of("A",List.of("Watchtower Teleport"),"B",List.of("Watchtower Teleport")),Map.of(),Map.of(),Map.of(8,1,7,1),Map.of(),Map.of(),7,Map.of(3,4),Map.of(),false,NAMES::get,id->id<7);
        assertEquals(Map.of(8,1,7,1),plan.quantities);
        assertEquals(Set.of("A","B"),plan.sites.get(8));
    }
    @Test public void actualUpstreamLabelsKeepPouchAndStaffAcrossUnknownAndFreeEdges()
    {
        var labels=BankRoutePlan.descriptions(List.of(
            Map.of("displayInfo","Teleport to House (Inside)"),
            Map.of("objectInfo","Enter Salve Graveyard Portal 37586","displayInfo","Salve Graveyard Portal"),
            Map.of("displayInfo","Lumbridge Home Teleport"),
            Map.of("objectInfo","Configure Fairy ring 29495","displayInfo","ALS"),
            Map.of("objectInfo","Charter Trader Crewmember 1330","displayInfo","2: Catherby")));
        var plan=RouteSupplies.plan(Set.of("A"),Map.of("A",labels),Map.of(),Map.of(),Map.of(7,1,772,1),Map.of(),Map.of(),7,Map.of(1,10,2,10,3,10),Map.of(),false,NAMES::get,id->id<7);
        assertEquals(Map.of(7,1,772,1),plan.quantities);assertEquals(Set.of("A"),plan.unresolved);
    }
    @Test public void homeAndMinigameTravelDoNotDiscardFairyStaff()
    {
        var plan=RouteSupplies.plan(Set.of("A"),Map.of("A",List.of("Lumbridge Home Teleport","Fishing Trawler Minigame Teleport",RouteSupplies.FAIRY)),Map.of(),Map.of(),Map.of(772,1),Map.of(),Map.of(),-1,Map.of(),Map.of(),false,NAMES::get,id->false);
        assertEquals(Map.of(772,1),plan.quantities);assertTrue(plan.unresolved.isEmpty());
    }
    @Test public void houseThenFairyRingIncludesBankedPouchAndLawRunes()
    {
        var routes=Map.of("A",List.of("Teleport to House",RouteSupplies.FAIRY),"B",List.of("Watchtower Teleport"));
        var plan=RouteSupplies.plan(Set.of("A","B"),routes,Map.of(),Map.of(),Map.of(7,1,772,1,3,10),Map.of(),Map.of(),7,Map.of(1,10,2,10),Map.of(3,10),false,NAMES::get,id->id<7);
        assertEquals(Map.of(7,1,772,1,3,3),plan.quantities);
        assertEquals(Set.of("A","B"),plan.sites.get(3));
    }
    @Test public void eliteAndEquippedStaffAndCombinationRuneAreHandledConservatively()
    {
        var plan=RouteSupplies.plan(Set.of("A"),Map.of("A",List.of("Teleport to House",RouteSupplies.FAIRY)),Map.of(),Map.of(),Map.of(3,1,6,1,8,1),Map.of(8,1),Map.of(8,1),-1,Map.of(),Map.of(3,1,6,1),true,NAMES::get,id->id<7);
        assertEquals(Map.of(3,1,6,1),plan.quantities);
    }
    @Test public void combinationRuneCoversTwoElementsOnlyWithinACast()
    {
        var route=Map.of("A",List.of("Teleport to House","Teleport to House"));
        var plan=RouteSupplies.plan(Set.of("A"),route,Map.of(),Map.of(),Map.of(3,2,6,2),Map.of(),Map.of(),-1,Map.of(),Map.of(3,2,6,2),false,NAMES::get,id->true);
        assertEquals(Map.of(3,2,6,2),plan.quantities);
        var insufficient=RouteSupplies.plan(Set.of("A"),route,Map.of(10,Set.of("A")),Map.of(),Map.of(3,2,6,1,10,1),Map.of(),Map.of(),-1,Map.of(),Map.of(3,2,6,1),false,NAMES::get,id->true);
        assertEquals(Map.of(3,1,6,1),insufficient.quantities);assertEquals(Set.of("A"),insufficient.unresolved);
    }
    @Test public void unresolvedSpellKeepsFallbackAndWalkingDoesNot()
    {
        var plan=RouteSupplies.plan(Set.of("A","B"),Map.of("A",List.of("Teleport to House"),"B",List.of()),Map.of(10,Set.of("A"),11,Set.of("B")),Map.of(),Map.of(10,1,11,1),Map.of(),Map.of(),-1,Map.of(),Map.of(),false,id->"tablet",id->true);
        assertEquals(Map.of(10,1),plan.quantities);
    }
    @Test public void unfundedSecondCastPreservesFirstAndDoesNotSpendFailedCast()
    {
        var sites=new LinkedHashSet<>(List.of("A","B","C"));
        var routes=Map.of("A",List.of("Teleport to House"),"B",List.of("Teleport to House","Ardougne Teleport"),
            "C",List.of("Teleport to House"));
        var plan=RouteSupplies.plan(sites,routes,Map.of(10,Set.of("B")),Map.of(),Map.of(3,2,6,2,10,1),
            Map.of(),Map.of(),-1,Map.of(),Map.of(3,2,6,2),false,NAMES::get,id->true);
        assertEquals(Map.of(3,2,6,2),plan.quantities);
        assertEquals(Set.of("A","B"),plan.sites.get(3));assertEquals(Set.of("B","C"),plan.unresolved);
    }
    @Test public void unresolvedFallbackCannotReuseTheOnlyTablet()
    {
        var plan=RouteSupplies.plan(Set.of("A","B"),Map.of("A",List.of("tablet")),Map.of(10,Set.of("B")),
            Map.of(10,List.of("tablet")),Map.of(10,1),Map.of(),Map.of(),-1,Map.of(),Map.of(),false,id->"tablet",id->true);
        assertEquals(Map.of(10,1),plan.quantities);
        assertEquals(Set.of("A"),plan.sites.get(10));
    }
}
