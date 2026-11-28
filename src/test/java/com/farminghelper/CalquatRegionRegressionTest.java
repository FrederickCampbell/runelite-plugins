package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.coords.WorldPoint;

public class CalquatRegionRegressionTest
{
    @Test public void allCalquatTargetsAreInTheirOwnFarmingRegion()
    {
        QuestFarmingStateReader reader=new QuestFarmingStateReader(null,null);
        for(String site:new String[]{"Tai Bwo Wannai","Great Conch","Kastori"})
        {
            FarmRunPatch p=new FarmRunPatch(site,"",PatchType.CALQUAT);
            WorldPoint marker=Waypoints.destination(p);
            assertNotNull(site,marker);
            assertTrue(site,reader.inPatchRegion("CALQUAT",site,"",marker));
            for(String other:new String[]{"Tai Bwo Wannai","Great Conch","Kastori"})
                if(!site.equals(other))assertFalse(site+" / "+other,reader.inPatchRegion("CALQUAT",other,"",marker));
        }
    }
}
