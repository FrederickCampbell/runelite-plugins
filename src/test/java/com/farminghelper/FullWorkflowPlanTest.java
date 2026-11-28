package com.farminghelper;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic full-catalog itinerary fixture, not a live-game simulation. */
public class FullWorkflowPlanTest
{
    @Test public void fullManualCatalogRetainsEveryPatchAndTerminalFossilGroup()
    {
        List<FarmRunPatch> all=FarmRunCatalog.patches();
        List<FarmRunPatch> route=RunOrder.flatten(RunOrder.shared(all,null,true,true));
        assertEquals(all.size(),route.size());assertEquals(new HashSet<>(all),new HashSet<>(route));
        List<String> expected=List.of("Farming Guild","Gnome Stronghold","Tree Gnome Village","Lumbridge Castle",
            "Varrock","Falador Park","Taverley","Catherby","Brimhaven","Tai Bwo Wannai","South Falador","Draynor Manor","Kastori","Auburnvale","Auburnvale belladonna");
        List<String> sites=new ArrayList<>();for(FarmRunPatch p:route)if(!sites.contains(RunOrder.site(p)))sites.add(RunOrder.site(p));
        assertEquals(expected,sites.subList(0,expected.size()));
        assertEquals("Fossil Island",sites.get(sites.size()-1));
        int last=0;for(FarmRunPatch p:route)if("Fossil Island".equals(RunOrder.site(p)))
        {
            int rank=p.getPatchType()==PatchType.HARDWOOD_TREE?1:p.getPatchType()==PatchType.SEAWEED?2:3;
            assertTrue(rank>=last);last=rank;
        }
    }
    @Test public void selectedSubsetDoesNotAddUnselectedGuildOrMainlandPlots()
    {
        FarmRunPatch a=new FarmRunPatch("Varrock","",PatchType.TREE);
        FarmRunPatch b=new FarmRunPatch("Catherby","",PatchType.HERB);
        assertEquals(List.of(a,b),RunOrder.flatten(RunOrder.shared(List.of(b,a),null,true,true)));
    }
}
