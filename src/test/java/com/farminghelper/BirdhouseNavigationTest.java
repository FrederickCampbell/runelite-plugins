package com.farminghelper;
import java.util.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
public class BirdhouseNavigationTest
{
    @Test public void fixedOrderAndDistinctTargets()
    {
        List<FarmRunPatch> birds=BirdhouseRun.patches();
        List<FarmRunPatch> reverse=new ArrayList<>(birds);Collections.reverse(reverse);
        assertEquals(birds,RunOrder.shared(reverse,null,true,false).get(0).patches);
        assertEquals("Tar Swamp entrance",birds.get(0).getName());
        assertEquals("Verdant Valley (Northeast)",birds.get(3).getName());
        Set<WorldPoint> targets=new HashSet<>();for(FarmRunPatch p:birds)targets.add(Waypoints.destination(p));
        assertEquals(4,targets.size());
    }
    @Test public void nativeTransportMetadataAndLocalOrigin()
    {
        WorldPoint origin=new WorldPoint(3763,3899,0),dest=new WorldPoint(3734,3893,0);
        List<TransportHint> hints=TransportHint.read(List.of(Map.of("origin",origin,"destination",dest,"objectInfo","Travel Rowboat 30919","displayInfo","")));
        assertEquals(1,hints.size());assertEquals(30919,hints.get(0).objectId);
        assertNotNull(TransportHint.nearby(hints,origin));assertNull(TransportHint.nearby(hints,dest));
        assertTrue(hints.get(0).instruction().contains("Return to Fossil Island"));
    }
}
