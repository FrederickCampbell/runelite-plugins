package com.farminghelper;

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
        QuestFarmingStateReader reader = new QuestFarmingStateReader(mock(Client.class), mock(ConfigManager.class));
        Set<String> keys = new HashSet<>();
        for (FarmRunPatch grouped : FarmRunCatalog.patches())
        {
            List<String> names = reader.patchNamesForType(grouped.getPatchType().name(), grouped.getLocation());
            assertFalse(grouped.getDisplayName(), names.isEmpty());
            assertEquals(grouped.getDisplayName(), grouped.getPatchCount(), names.size());
            for (FarmRunPatch patch : PatchNames.expand(grouped, names))
            {
                assertTrue(patch.getDisplayName(), keys.add(patch.key()));
                assertNotNull(patch.getDisplayName(), Waypoints.destination(patch));
            }
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
            StepEngine.action(PatchType.CACTUS, CropState.UNCHECKED, false, null));
    }
}
