package com.farminghelper;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;

import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class PatchCoverageTest
{
    @Test public void everyPhysicalPatchHasAUniqueIdentityAndDefinedDestination()
    {
        Set<String> keys = new HashSet<>();
        for(FarmRunPatch patch:FarmRunCatalog.patches())
        {
            assertTrue(patch.key(),keys.add(patch.key()));
            assertNotNull(patch.key(),Waypoints.destination(patch));
        }
        assertEquals("All physical plots and compost bins in the vendored farming registry", 107, keys.size());
    }

    @Test public void mapMarkersAreNotExactObjectTargets()
    {
        FarmRunPatch patch = new FarmRunPatch("Yanille","",PatchType.HOPS);
        assertNotNull(Waypoints.destination(patch));
        assertNull(QuestTargets.get(patch));
    }

    @Test public void currentSpiritTreeLimits()
    {
        assertEquals(0, FarmingHelperPlugin.spiritCapacity(82));
        assertEquals(1, FarmingHelperPlugin.spiritCapacity(83));
        assertEquals(2, FarmingHelperPlugin.spiritCapacity(88));
        assertEquals(3, FarmingHelperPlugin.spiritCapacity(93));
        assertEquals(4, FarmingHelperPlugin.spiritCapacity(96));
        assertEquals(Integer.MAX_VALUE, FarmingHelperPlugin.spiritCapacity(99));
    }

    @Test public void existingPerennialsDoNotRequireReplacementSeeds()
    {
        assertTrue(StepEngine.keepExisting(PatchType.CACTUS, CropState.HARVESTABLE));
        assertTrue(StepEngine.keepExisting(PatchType.BUSH, CropState.HARVESTABLE));
        assertTrue(StepEngine.keepExisting(PatchType.REDWOOD, CropState.UNCHECKED));
        assertFalse(StepEngine.keepExisting(PatchType.HERB, CropState.HARVESTABLE));
        assertFalse(StepEngine.keepExisting(PatchType.CACTUS, CropState.EMPTY));
        assertEquals(StepEngine.Action.CHECK_HEALTH,
            StepEngine.action(PatchType.CACTUS, new Observation(CropState.UNCHECKED,Source.LIVE,1,null,"",0),false,true,false,false));
    }
}
