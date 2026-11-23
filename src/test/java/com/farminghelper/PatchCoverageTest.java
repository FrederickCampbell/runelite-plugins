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
    @Test public void everyCatalogPatchHasStateIdentityAndRoutableArea()
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
                assertTrue(patch.getDisplayName(), keys.add(FarmRouteManager.key(patch)));
                assertTrue(patch.getDisplayName(), TravelTarget.navigation(patch, reader).routable());
            }
        }
        assertEquals("All non-compost physical plots in the vendored farming registry", 99, keys.size());
    }

    @Test public void mapMarkersAreNotExactObjectTargets()
    {
        QuestFarmingStateReader reader = new QuestFarmingStateReader(mock(Client.class), mock(ConfigManager.class));
        TravelTarget target = TravelTarget.navigation(new FarmRunPatch("Yanille", "", PatchType.HOPS), reader);
        assertTrue(target.routable());
        assertFalse(target.exact);
    }

    @Test public void currentSpiritTreeLimits()
    {
        assertEquals(0, RunReadiness.spiritTreeCapacity(82));
        assertEquals(1, RunReadiness.spiritTreeCapacity(83));
        assertEquals(2, RunReadiness.spiritTreeCapacity(88));
        assertEquals(3, RunReadiness.spiritTreeCapacity(93));
        assertEquals(4, RunReadiness.spiritTreeCapacity(96));
        assertEquals(Integer.MAX_VALUE, RunReadiness.spiritTreeCapacity(99));
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
